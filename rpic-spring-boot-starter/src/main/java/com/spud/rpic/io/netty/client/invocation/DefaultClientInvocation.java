package com.spud.rpic.io.netty.client.invocation;

import com.spud.rpic.cluster.CircuitBreakerManager;
import com.spud.rpic.cluster.EndpointStatsRegistry;
import com.spud.rpic.cluster.LoadBalancer;
import com.spud.rpic.common.domain.RpcRequest;
import com.spud.rpic.common.domain.RpcResponse;
import com.spud.rpic.common.exception.RemoteException;
import com.spud.rpic.common.exception.RpcException;
import com.spud.rpic.common.exception.ServiceNotFoundException;
import com.spud.rpic.common.exception.TimeoutException;
import com.spud.rpic.io.netty.NetClient;
import com.spud.rpic.model.ServiceMetadata;
import com.spud.rpic.model.ServiceURL;
import com.spud.rpic.property.RpcClientProperties;
import com.spud.rpic.registry.Registry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.naming.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Slf4j
public class DefaultClientInvocation implements ClientInvocation {

	private final Registry registry;

	private final LoadBalancer loadBalancer;

	private final NetClient netClient;

	private final RpcClientProperties clientProperties;

	private final CircuitBreakerManager circuitBreakerManager;

	private final EndpointStatsRegistry endpointStatsRegistry;

	public DefaultClientInvocation(Registry registry, LoadBalancer loadBalancer, NetClient netClient,
		RpcClientProperties clientProperties,
		CircuitBreakerManager circuitBreakerManager,
		EndpointStatsRegistry endpointStatsRegistry) {
		this.registry = registry;
		this.loadBalancer = loadBalancer;
		this.netClient = netClient;
		this.clientProperties = clientProperties;
		this.circuitBreakerManager = circuitBreakerManager;
		this.endpointStatsRegistry = endpointStatsRegistry;
	}

	@Override
	public RpcResponse invoke(ServiceMetadata metadata, RpcRequest request, int timeout)
		throws Exception {
		RpcClientProperties.RetryProperties retryProps = clientProperties.getRetry();
		int maxAttempts = Math.max(1, retryProps.isEnabled() ? retryProps.getMaxAttempts() : 1);
		long overallTimeout = timeout > 0 ? timeout : clientProperties.getTimeout();
		long deadlineAtMillis = System.currentTimeMillis() + overallTimeout;

		Throwable lastException = null;
		Set<String> attemptedEndpoints = new HashSet<>();
		int attempt = 1;

		while (attempt <= maxAttempts) {
			long remaining = deadlineAtMillis - System.currentTimeMillis();
			if (remaining <= 0) {
				throw new TimeoutException("Request deadline exceeded", lastException);
			}

			ServiceURL selected = selectHealthyInstance(metadata, attemptedEndpoints);
			if (selected == null) {
				throw new ServiceUnavailableException(
					"No healthy instance available for service: " + metadata.getServiceKey());
			}

			String endpoint = selected.getAddress();
			if (!circuitBreakerManager.tryAcquirePermission(endpoint)) {
				attemptedEndpoints.add(endpoint);
				lastException = new ServiceUnavailableException(
					"Circuit breaker open for endpoint: " + endpoint);
				continue;
			}

			long perAttemptTimeout = Math.min(remaining, Integer.MAX_VALUE);
			request.setDeadlineAtMillis(deadlineAtMillis);
			request.setAttempt(attempt);
			request.setTimeout((int) perAttemptTimeout);

			long startNanos = System.nanoTime();
			try {
				RpcResponse response = netClient.send(selected, request, (int) perAttemptTimeout);
				long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
				circuitBreakerManager.onSuccess(endpoint, latencyMs);
				endpointStatsRegistry.onSuccess(endpoint, latencyMs);
				return response;
			} catch (Exception ex) {
				long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
				circuitBreakerManager.onError(endpoint, ex, latencyMs);
				endpointStatsRegistry.onFailure(endpoint, latencyMs, ex);
				attemptedEndpoints.add(endpoint);
				lastException = ex;

				if (!shouldRetry(ex, retryProps, attempt, maxAttempts)) {
					if (ex instanceof Exception) {
						throw ex;
					}
					throw new RpcException("Invocation failed", ex);
				}

				long backoff = Math.min(computeBackoffMillis(retryProps, attempt),
					deadlineAtMillis - System.currentTimeMillis());
				if (backoff > 0) {
					TimeUnit.MILLISECONDS.sleep(backoff);
				}
				attempt++;
			}
		}
		if (lastException instanceof Exception) {
			throw (Exception) lastException;
		}
		throw new RpcException("Invocation failed", lastException);
	}

	@Override
	public CompletableFuture<RpcResponse> invokeAsync(ServiceMetadata metadata, RpcRequest request,
		int timeout) {
		CompletableFuture<RpcResponse> future = new CompletableFuture<>();
		try {
			ServiceURL selected = selectHealthyInstance(metadata, new HashSet<>());
			if (selected == null) {
				future.completeExceptionally(
					new ServiceUnavailableException("No healthy instance available"));
				return future;
			}
			long overallTimeout = timeout > 0 ? timeout : clientProperties.getTimeout();
			long deadlineAtMillis = System.currentTimeMillis() + overallTimeout;
			request.setDeadlineAtMillis(deadlineAtMillis);
			request.setAttempt(1);
			int perAttemptTimeout = (int) Math.min(Integer.MAX_VALUE, overallTimeout);
			request.setTimeout(perAttemptTimeout);
			return netClient.sendAsync(selected, request, perAttemptTimeout);
		} catch (Exception e) {
			future.completeExceptionally(
				new RpcException("Failed to invoke remote service: " + metadata.getServiceId(), e));
			return future;
		}
	}

	private ServiceURL selectHealthyInstance(ServiceMetadata metadata, Set<String> attemptedEndpoints)
		throws Exception {
		List<ServiceURL> instances = registry.discover(metadata);
		if (instances == null || instances.isEmpty()) {
			throw new ServiceNotFoundException(
				"No available instances for service: " + metadata.getServiceKey());
		}

		List<ServiceURL> candidates = new ArrayList<>();
		long now = System.currentTimeMillis();
		for (ServiceURL url : instances) {
			String endpoint = url.getAddress();
			if (attemptedEndpoints.contains(endpoint)) {
				continue;
			}
			if (endpointStatsRegistry.isEjected(endpoint, now)) {
				continue;
			}
			if (!circuitBreakerManager.isCallPermitted(endpoint)) {
				continue;
			}
			candidates.add(url);
		}

		if (candidates.isEmpty()) {
			return null;
		}

		return loadBalancer.select(new ArrayList<>(candidates));
	}

	private boolean shouldRetry(Throwable throwable, RpcClientProperties.RetryProperties retryProps,
		int attempt, int maxAttempts) {
		if (!retryProps.isEnabled() || attempt >= maxAttempts) {
			return false;
		}

		Throwable cause = throwable;
		while (cause != null) {
			if (cause instanceof TimeoutException || cause instanceof RemoteException) {
				return true;
			}
			String name = cause.getClass().getName();
			if (retryProps.getRetryOnExceptions().stream().anyMatch(cfg -> cfg.equals(name))) {
				return true;
			}
			String message = cause.getMessage();
			if (message != null) {
				for (String keyword : retryProps.getRetryOnErrorMsgContains()) {
					if (message.toUpperCase().contains(keyword)) {
						return true;
					}
				}
			}
			cause = cause.getCause();
		}
		return false;
	}

	private long computeBackoffMillis(RpcClientProperties.RetryProperties retryProps, int attempt) {
		double base = retryProps.getBaseDelayMs();
		double multiplier = retryProps.getMultiplier();
		double delay = base * Math.pow(multiplier, Math.max(0, attempt - 1));
		delay = Math.min(delay, retryProps.getMaxDelayMs());
		double jitterFactor = Math.max(0d, Math.min(1d, retryProps.getJitterFactor()));
		double jitter = jitterFactor * ThreadLocalRandom.current().nextDouble();
		delay = delay * (1 - jitterFactor + jitter);
		return (long) Math.max(0, delay);
	}
}