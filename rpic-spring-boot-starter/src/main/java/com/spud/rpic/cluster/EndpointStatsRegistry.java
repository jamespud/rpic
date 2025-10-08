package com.spud.rpic.cluster;

import com.spud.rpic.property.RpcClientProperties;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 维护客户端侧端点统计，用于负载均衡与异常节点剔除。
 */
public class EndpointStatsRegistry {

	private static final double DEFAULT_EWMA_ALPHA = 0.2d;

	private final RpcClientProperties.OutlierEjectionProperties properties;

	private final Map<String, Stats> statsMap = new ConcurrentHashMap<>();

	public EndpointStatsRegistry(RpcClientProperties clientProperties) {
		this.properties = clientProperties.getOutlier();
	}

	public void onSuccess(String endpoint, long latencyMs) {
		Stats stats = statsMap.computeIfAbsent(endpoint, key -> new Stats());
		stats.recordSuccess(latencyMs);
	}

	public void onFailure(String endpoint, long latencyMs, Throwable cause) {
		Stats stats = statsMap.computeIfAbsent(endpoint, key -> new Stats());
		stats.recordFailure(latencyMs);
		if (properties.isEnabled()) {
			stats.maybeEject(properties);
		}
	}

	public boolean isEjected(String endpoint, long now) {
		if (!properties.isEnabled()) {
			return false;
		}
		Stats stats = statsMap.get(endpoint);
		return stats != null && stats.isEjected(now, properties.getProbeIntervalMs());
	}

	public double getLatencyScore(String endpoint) {
		Stats stats = statsMap.get(endpoint);
		return stats != null ? stats.getEwmaLatency() : Double.NaN;
	}

	private static final class Stats {

		private double ewmaLatency;
		private final AtomicLong requestCount = new AtomicLong();
		private final AtomicLong failureCount = new AtomicLong();
		private volatile long ejectedUntil;
		private volatile long lastProbeAt;

		void recordSuccess(long latencyMs) {
			requestCount.incrementAndGet();
			updateEwma(latencyMs);
		}

		void recordFailure(long latencyMs) {
			requestCount.incrementAndGet();
			failureCount.incrementAndGet();
			updateEwma(latencyMs <= 0 ? 1 : latencyMs);
		}

		void maybeEject(RpcClientProperties.OutlierEjectionProperties properties) {
			long requests = requestCount.get();
			if (!properties.isEnabled() || requests < properties.getMinRequestVolume()) {
				return;
			}
			double failureRate = failureCount.get() / (double) Math.max(1, requests);
			if (failureRate >= properties.getErrorRateThreshold()) {
				ejectedUntil = System.currentTimeMillis() + properties.getEjectionDurationMs();
			}
		}

		boolean isEjected(long now, long probeIntervalMs) {
			if (ejectedUntil <= now) {
				return false;
			}
			if (probeIntervalMs <= 0) {
				return true;
			}
			if (lastProbeAt == 0 || now - lastProbeAt >= probeIntervalMs) {
				lastProbeAt = now;
				return false;
			}
			return true;
		}

		double getEwmaLatency() {
			return ewmaLatency;
		}

		private void updateEwma(long latencyMs) {
			if (latencyMs <= 0) {
				latencyMs = 1;
			}
			if (ewmaLatency <= 0) {
				ewmaLatency = latencyMs;
			} else {
				ewmaLatency = ewmaLatency * (1 - DEFAULT_EWMA_ALPHA) + latencyMs * DEFAULT_EWMA_ALPHA;
			}
		}
	}
}
