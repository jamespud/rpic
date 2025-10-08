package com.spud.rpic.cluster;

import com.spud.rpic.property.RpcClientProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * 管理每个端点的熔断器。
 */
public class CircuitBreakerManager {

	private final RpcClientProperties.CircuitBreakerProperties properties;

	private final CircuitBreakerRegistry registry;

	private final ConcurrentMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

	public CircuitBreakerManager(RpcClientProperties clientProperties) {
		this.properties = clientProperties.getCircuitBreaker();
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

	private CircuitBreaker getBreaker(String endpoint) {
		Objects.requireNonNull(endpoint, "endpoint");
		String key = properties.isPerEndpoint() ? endpoint : "global";
		return breakers.computeIfAbsent(key, this::createBreaker);
	}

	private CircuitBreaker createBreaker(String key) {
		if (!isEnabled()) {
			throw new IllegalStateException("Circuit breaker disabled");
		}
		return registry.circuitBreaker(key);
	}

	private boolean isEnabled() {
		return registry != null && properties.isEnabled();
	}
}
