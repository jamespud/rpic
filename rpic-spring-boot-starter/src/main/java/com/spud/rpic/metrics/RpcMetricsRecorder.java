package com.spud.rpic.metrics;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import com.spud.rpic.property.RpcProperties;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
	private static final RpcMetricsRecorder NOOP = new RpcMetricsRecorder();

	private final MeterRegistry registry;
	private final RpcProperties.MetricsProperties properties;
	private final boolean enabled;

	private final Timer.Builder clientTimerBuilder;
	private final Counter.Builder clientRequestCounterBuilder;
	private final Counter.Builder clientErrorCounterBuilder;
	private final DistributionSummary.Builder clientRequestBytesBuilder;
	private final DistributionSummary.Builder clientResponseBytesBuilder;

	private final Timer.Builder serverTimerBuilder;
	private final Counter.Builder serverRequestCounterBuilder;
	private final Counter.Builder serverErrorCounterBuilder;
	private final DistributionSummary.Builder serverRequestBytesBuilder;
	private final DistributionSummary.Builder serverResponseBytesBuilder;

	private final Counter.Builder poolAcquireCounterBuilder;
	private final Counter.Builder poolAcquireErrorCounterBuilder;
	private final ConcurrentMap<String, Boolean> activeGaugeCache = new ConcurrentHashMap<>();

	private RpcMetricsRecorder() {
		this.registry = null;
		this.properties = null;
		this.enabled = false;
		this.clientTimerBuilder = null;
		this.clientRequestCounterBuilder = null;
		this.clientErrorCounterBuilder = null;
		this.clientRequestBytesBuilder = null;
		this.clientResponseBytesBuilder = null;
		this.serverTimerBuilder = null;
		this.serverRequestCounterBuilder = null;
		this.serverErrorCounterBuilder = null;
		this.serverRequestBytesBuilder = null;
		this.serverResponseBytesBuilder = null;
		this.poolAcquireCounterBuilder = null;
		this.poolAcquireErrorCounterBuilder = null;
	}

	private RpcMetricsRecorder(MeterRegistry registry, RpcProperties.MetricsProperties properties) {
		this.registry = registry;
		this.properties = properties;
		this.enabled = registry != null && properties != null && properties.isEnabled();

		if (!enabled) {
			this.clientTimerBuilder = null;
			this.clientRequestCounterBuilder = null;
			this.clientErrorCounterBuilder = null;
			this.clientRequestBytesBuilder = null;
			this.clientResponseBytesBuilder = null;
			this.serverTimerBuilder = null;
			this.serverRequestCounterBuilder = null;
			this.serverErrorCounterBuilder = null;
			this.serverRequestBytesBuilder = null;
			this.serverResponseBytesBuilder = null;
			this.poolAcquireCounterBuilder = null;
			this.poolAcquireErrorCounterBuilder = null;
			return;
		}

		RpcProperties.MetricsProperties props = Objects.requireNonNull(properties);

		double[] percentiles = props.getPercentiles() != null ? props.getPercentiles() : new double[0];
		Duration[] slaDurations = props.getSlaMs() != null
			? Arrays.stream(props.getSlaMs()).mapToObj(Duration::ofMillis).toArray(Duration[]::new)
			: new Duration[0];
		boolean publishHistogram = props.isHistogram();

		this.clientTimerBuilder = configureTimer(Timer.builder("rpic.client.latency")
			.description("RPC client latency"), percentiles, slaDurations, publishHistogram);
		this.clientRequestCounterBuilder = Counter.builder("rpic.client.requests")
			.description("Total RPC client requests");
		this.clientErrorCounterBuilder = Counter.builder("rpic.client.errors")
			.description("Total RPC client errors");
		this.clientRequestBytesBuilder = DistributionSummary.builder("rpic.client.request.bytes")
			.description("RPC client request payload size")
			.baseUnit("bytes");
		this.clientResponseBytesBuilder = DistributionSummary.builder("rpic.client.response.bytes")
			.description("RPC client response payload size")
			.baseUnit("bytes");

		this.serverTimerBuilder = configureTimer(Timer.builder("rpic.server.latency")
			.description("RPC server latency"), percentiles, slaDurations, publishHistogram);
		this.serverRequestCounterBuilder = Counter.builder("rpic.server.requests")
			.description("Total RPC server requests");
		this.serverErrorCounterBuilder = Counter.builder("rpic.server.errors")
			.description("Total RPC server errors");
		this.serverRequestBytesBuilder = DistributionSummary.builder("rpic.server.request.bytes")
			.description("RPC server request payload size")
			.baseUnit("bytes");
		this.serverResponseBytesBuilder = DistributionSummary.builder("rpic.server.response.bytes")
			.description("RPC server response payload size")
			.baseUnit("bytes");

		this.poolAcquireCounterBuilder = Counter.builder("rpic.client.pool.acquire")
			.description("Successful RPC client pool acquires");
		this.poolAcquireErrorCounterBuilder = Counter.builder("rpic.client.pool.acquire.errors")
			.description("Failed RPC client pool acquires");
	}

	private Timer.Builder configureTimer(Timer.Builder builder, double[] percentiles, Duration[] slaDurations,
	                                     boolean publishHistogram) {
		if (percentiles.length > 0) {
			builder.publishPercentiles(percentiles);
		}
		if (slaDurations.length > 0) {
			builder.sla(slaDurations);
		}
		builder.publishPercentileHistogram(publishHistogram);
		return builder;
	}

	public static RpcMetricsRecorder create(MeterRegistry registry, RpcProperties.MetricsProperties properties) {
		if (registry == null || properties == null || !properties.isEnabled()) {
			return NOOP;
		}
		return new RpcMetricsRecorder(registry, properties);
	}

	public Timer.Sample startClientSample() {
		return enabled ? Timer.start(registry) : null;
	}

	public Timer.Sample startServerSample() {
		return enabled ? Timer.start(registry) : null;
	}

	public void recordClient(Timer.Sample sample, String service, String method, String endpoint, boolean success,
	                         Throwable error, long requestBytes, long responseBytes) {
		recordClient(sample, service, method, endpoint, success, error, requestBytes, responseBytes, null, null);
	}

	public void recordClient(Timer.Sample sample, String service, String method, String endpoint, boolean success,
	                         Throwable error, long requestBytes, long responseBytes, Boolean retried, Integer attempt) {
		if (!enabled) {
			return;
		}
		Iterable<Tag> tags = clientTags(service, method, endpoint, success);
		Tag[] extra = clientExtraTags(retried, attempt);
		Iterable<Tag> merged = mergeTags(tags, extra);
		Timer timer = clientTimerBuilder.tags(merged).register(registry);
		if (sample != null) {
			sample.stop(timer);
		}
		clientRequestCounterBuilder.tags(merged).register(registry).increment();
		if (!success) {
			clientErrorCounterBuilder.tags(merged).tag("error", errorTag(error)).register(registry).increment();
		}
		recordBytes(clientRequestBytesBuilder, merged, requestBytes);
		recordBytes(clientResponseBytesBuilder, merged, responseBytes);
	}

	public void recordServer(Timer.Sample sample, String service, String method, String caller, boolean success,
	                         Throwable error, long requestBytes, long responseBytes) {
		recordServer(sample, service, method, caller, success, error, requestBytes, responseBytes, new Tag[0]);
	}

	public void recordServer(Timer.Sample sample, String service, String method, String caller, boolean success,
	                         Throwable error, long requestBytes, long responseBytes, Tag... extraTags) {
		if (!enabled) {
			return;
		}
		Iterable<Tag> base = serverTags(service, method, caller, success);
		Iterable<Tag> merged = mergeTags(base, extraTags);
		Timer timer = serverTimerBuilder.tags(merged).register(registry);
		if (sample != null) {
			sample.stop(timer);
		}
		serverRequestCounterBuilder.tags(merged).register(registry).increment();
		if (!success) {
			serverErrorCounterBuilder.tags(merged).tag("error", errorTag(error)).register(registry).increment();
		}
		recordBytes(serverRequestBytesBuilder, merged, requestBytes);
		recordBytes(serverResponseBytesBuilder, merged, responseBytes);
	}

	public void recordPoolAcquire(String endpoint, boolean success) {
		if (!enabled) {
			return;
		}
		Counter.Builder builder = success ? poolAcquireCounterBuilder : poolAcquireErrorCounterBuilder;
		builder.tags(Tags.of("endpoint", safeEndpoint(endpoint))).register(registry).increment();
	}

	public void registerActiveConnectionsGauge(String endpoint, Supplier<Number> supplier) {
		if (!enabled) {
			return;
		}
		String safeEndpoint = safeEndpoint(endpoint);
		activeGaugeCache.computeIfAbsent(safeEndpoint, key -> {
			try {
				Gauge.builder("rpic.client.pool.active", supplier)
					.description("Active connections per endpoint")
					.tag("endpoint", key)
					.register(registry);
			} catch (IllegalArgumentException ignored) {
				// gauge already registered with same tags
			}
			return Boolean.TRUE;
		});
	}

	private void recordBytes(DistributionSummary.Builder builder, Iterable<Tag> tags, long bytes) {
		if (bytes < 0) {
			return;
		}
		builder.tags(tags).register(registry).record(bytes);
	}

	private Tag[] clientExtraTags(Boolean retried, Integer attempt) {
		if (retried == null && (attempt == null || !properties.isHighCardinalityTagsEnabled())) {
			return new Tag[0];
		}
		java.util.List<Tag> extras = new java.util.ArrayList<>();
		if (retried != null) {
			extras.add(Tag.of("retried", Boolean.toString(retried)));
		}
		if (attempt != null && properties.isHighCardinalityTagsEnabled()) {
			extras.add(Tag.of("attempt", Integer.toString(Math.max(1, attempt))));
		}
		return extras.toArray(new Tag[0]);
	}

	private Iterable<Tag> mergeTags(Iterable<Tag> base, Tag[] extras) {
		if (extras == null || extras.length == 0) {
			return base;
		}
		Tags merged = Tags.empty();
		for (Tag tag : base) {
			merged = merged.and(tag);
		}
		for (Tag tag : extras) {
			merged = merged.and(tag);
		}
		return merged;
	}

	private Iterable<Tag> clientTags(String service, String method, String endpoint, boolean success) {
		return Tags.of(
			"service", safeService(service),
			"method", methodTag(method),
			"endpoint", safeEndpoint(endpoint),
			"success", Boolean.toString(success));
	}

	private Iterable<Tag> serverTags(String service, String method, String caller, boolean success) {
		return Tags.of(
			"service", safeService(service),
			"method", methodTag(method),
			"caller", safeEndpoint(caller),
			"success", Boolean.toString(success));
	}

	private String safeService(String value) {
		return value == null || value.isEmpty() ? "unknown" : value;
	}

	private String methodTag(String method) {
		if (!enabled) {
			return "unknown";
		}
		if (!properties.isHighCardinalityTagsEnabled()) {
			return "_";
		}
		return method == null || method.isEmpty() ? "unknown" : method;
	}

	private String safeEndpoint(String endpoint) {
		return endpoint == null || endpoint.isEmpty() ? "unknown" : endpoint;
	}

	private String errorTag(Throwable throwable) {
		if (throwable == null) {
			return "none";
		}
		return throwable.getClass().getSimpleName();
	}

	public boolean isEnabled() {
		return enabled;
	}
}
