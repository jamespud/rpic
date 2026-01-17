package com.spud.rpic.cluster;

import com.spud.rpic.property.RpcClientProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerEvent;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * 管理每个端点的熔断器。
 */
@Slf4j
public class CircuitBreakerManager implements MeterBinder {

	private final RpcClientProperties.CircuitBreakerProperties properties;

	private final CircuitBreakerRegistry registry;

	private final ConcurrentMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

	private final MeterRegistry meterRegistry;

	public CircuitBreakerManager(RpcClientProperties clientProperties) {
		this(clientProperties, null);
	}

	public CircuitBreakerManager(RpcClientProperties clientProperties, MeterRegistry meterRegistry) {
		this.properties = clientProperties.getCircuitBreaker();
		this.meterRegistry = meterRegistry;
		if (properties.isEnabled()) {
			CircuitBreakerConfig config = CircuitBreakerConfig.custom()
				.failureRateThreshold(properties.getFailureRateThreshold())
				.slowCallRateThreshold(properties.getSlowCallRateThreshold())
				.slowCallDurationThreshold(Duration.ofMillis(properties.getSlowCallDurationThresholdMs()))
				.permittedNumberOfCallsInHalfOpenState(
					Math.max(1, properties.getMinimumNumberOfCalls() / 2))
				.slidingWindowSize(properties.getSlidingWindowSize())
				.minimumNumberOfCalls(properties.getMinimumNumberOfCalls())
				.waitDurationInOpenState(Duration.ofMillis(properties.getWaitDurationInOpenStateMs()))
				.build();
			this.registry = CircuitBreakerRegistry.of(config);
		} else {
			this.registry = null;
		}
	}

	public boolean isCallPermitted(String endpoint) {
		if (!isEnabled()) {
			return true;
		}
		CircuitBreaker breaker = getBreaker(endpoint);
		return breaker.getState() != CircuitBreaker.State.OPEN;
	}

	public boolean tryAcquirePermission(String endpoint) {
		if (!isEnabled()) {
			return true;
		}
		CircuitBreaker breaker = getBreaker(endpoint);
		return breaker.tryAcquirePermission();
	}

	public void onSuccess(String endpoint, long latencyMs) {
		if (!isEnabled()) {
			return;
		}
		getBreaker(endpoint).onSuccess(Math.max(0, latencyMs), TimeUnit.MILLISECONDS);
	}

	public void onError(String endpoint, Throwable throwable, long latencyMs) {
		if (!isEnabled()) {
			return;
		}
		CircuitBreaker breaker = getBreaker(endpoint);
		breaker.onError(Math.max(0, latencyMs), TimeUnit.MILLISECONDS, throwable);
	}

	/**
	 * 获取熔断器的状态信息
	 * @param endpoint 端点地址
	 * @return 熔断器状态（OPEN, CLOSED, HALF_OPEN），或 null 表示未启用
	 */
	public CircuitBreaker.State getState(String endpoint) {
		if (!isEnabled()) {
			return null;
		}
		return getBreaker(endpoint).getState();
	}

	/**
	 * 获取熔断器的详细统计信息
	 * @param endpoint 端点地址
	 * @return 熔断器统计信息
	 */
	public CircuitBreaker.Metrics getMetrics(String endpoint) {
		if (!isEnabled()) {
			return null;
		}
		return getBreaker(endpoint).getMetrics();
	}

	/**
	 * 重置熔断器
	 * @param endpoint 端点地址
	 */
	public void reset(String endpoint) {
		if (!isEnabled()) {
			return;
		}
		getBreaker(endpoint).reset();
	}

	private CircuitBreaker getBreaker(String endpoint) {
		Objects.requireNonNull(endpoint, "endpoint");
		String key = properties.isPerEndpoint() ? endpoint : "global";
		return breakers.computeIfAbsent(key, this::createBreaker);
	}

	private CircuitBreaker createBreaker(String key) {
		if (!isEnabled()) {
			throw new IllegalStateException("Circuit breaker disabled");
		}
		CircuitBreaker breaker = registry.circuitBreaker(key);
		// 添加事件监听器
		breaker.getEventPublisher()
			.onStateTransition(this::onStateTransition)
			.onError(this::onErrorEvent)
			.onSuccess(this::onSuccessEvent);
		return breaker;
	}

	private void onStateTransition(CircuitBreakerOnStateTransitionEvent event) {
		log.info("Circuit breaker [{}] transitioned from {} to {}",
			event.getCircuitBreakerName(),
			event.getStateTransition().getFromState(),
			event.getStateTransition().getToState());
	}

	private void onErrorEvent(CircuitBreakerEvent event) {
		log.debug("Circuit breaker [{}] error: {}", event.getCircuitBreakerName(), event.getEventType());
	}

	private void onSuccessEvent(CircuitBreakerEvent event) {
		log.debug("Circuit breaker [{}] success: {}", event.getCircuitBreakerName(), event.getEventType());
	}

	private boolean isEnabled() {
		return registry != null && properties.isEnabled();
	}

	@Override
	public void bindTo(MeterRegistry meterRegistry) {
		// 如果没有启用熔断器，直接返回
		if (!isEnabled()) {
			return;
		}

		// 为每个熔断器创建指标
		breakers.forEach((key, breaker) -> registerBreakerMetrics(meterRegistry, key, breaker));
	}

	private void registerBreakerMetrics(MeterRegistry meterRegistry, String key, CircuitBreaker breaker) {
		// 熔断器状态指标 (0: CLOSED, 1: OPEN, 2: HALF_OPEN)
		Gauge.builder("rpic.circuitbreaker.state", breaker, this::getStateValue)
			.tags("endpoint", key)
			.description("Circuit breaker state (0: CLOSED, 1: OPEN, 2: HALF_OPEN)")
			.register(meterRegistry);

		// 熔断器失败率指标
		Gauge.builder("rpic.circuitbreaker.failure.rate", breaker.getMetrics(), CircuitBreaker.Metrics::getFailureRate)
			.tags("endpoint", key)
			.description("Circuit breaker failure rate percentage")
			.register(meterRegistry);

		// 熔断器慢调用率指标
		Gauge.builder("rpic.circuitbreaker.slow.call.rate", breaker.getMetrics(), CircuitBreaker.Metrics::getSlowCallRate)
			.tags("endpoint", key)
			.description("Circuit breaker slow call rate percentage")
			.register(meterRegistry);

		// 熔断器当前窗口调用数指标
		Gauge.builder("rpic.circuitbreaker.calls", breaker.getMetrics(), CircuitBreaker.Metrics::getNumberOfSuccessfulCalls)
			.tags("endpoint", key)
			.description("Number of calls in current sliding window")
			.register(meterRegistry);

		// 熔断器失败调用数指标
		Gauge.builder("rpic.circuitbreaker.failed.calls", breaker.getMetrics(), CircuitBreaker.Metrics::getNumberOfFailedCalls)
			.tags("endpoint", key)
			.description("Number of failed calls in current sliding window")
			.register(meterRegistry);

		// 熔断器慢调用数指标
		Gauge.builder("rpic.circuitbreaker.slow.calls", breaker.getMetrics(), CircuitBreaker.Metrics::getNumberOfSlowCalls)
			.tags("endpoint", key)
			.description("Number of slow calls in current sliding window")
			.register(meterRegistry);
	}

	private int getStateValue(CircuitBreaker breaker) {
		switch (breaker.getState()) {
			case CLOSED:
				return 0;
			case OPEN:
				return 1;
			case HALF_OPEN:
				return 2;
			default:
				return -1;
		}
	}
}
