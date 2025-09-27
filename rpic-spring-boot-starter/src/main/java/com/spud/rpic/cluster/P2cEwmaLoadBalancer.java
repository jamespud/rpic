package com.spud.rpic.cluster;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.spud.rpic.model.ServiceURL;

/**
 * 基于P2C+EWMA的负载均衡实现。
 */
public class P2cEwmaLoadBalancer implements LoadBalancer {

	private final EndpointStatsRegistry endpointStatsRegistry;

	public P2cEwmaLoadBalancer(EndpointStatsRegistry endpointStatsRegistry) {
		this.endpointStatsRegistry = endpointStatsRegistry;
	}

	@Override
	public ServiceURL select(List<ServiceURL> urls) {
		if (urls == null || urls.isEmpty()) {
			return null;
		}

		long now = System.currentTimeMillis();
		List<ServiceURL> candidates = new ArrayList<>(urls.size());
		for (ServiceURL url : urls) {
			if (!endpointStatsRegistry.isEjected(url.getAddress(), now)) {
				candidates.add(url);
			}
		}

		if (candidates.isEmpty()) {
			candidates = urls;
		}

		if (candidates.size() == 1) {
			return candidates.get(0);
		}

		ServiceURL first = randomPick(candidates);
		ServiceURL second = randomPick(candidates);
		if (second == first && candidates.size() > 1) {
			int index = (candidates.indexOf(first) + 1) % candidates.size();
			second = candidates.get(index);
		}

		return better(first, second);
	}

	@Override
	public String getType() {
		return LoadBalancerType.P2C_EWMA.getType();
	}

	private ServiceURL randomPick(List<ServiceURL> list) {
		return list.get(ThreadLocalRandom.current().nextInt(list.size()));
	}

	private ServiceURL better(ServiceURL a, ServiceURL b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		double scoreA = score(a);
		double scoreB = score(b);
		if (Double.isNaN(scoreA) && Double.isNaN(scoreB)) {
			return ThreadLocalRandom.current().nextBoolean() ? a : b;
		}
		if (Double.isNaN(scoreA)) {
			return b;
		}
		if (Double.isNaN(scoreB)) {
			return a;
		}
		return scoreA <= scoreB ? a : b;
	}

	private double score(ServiceURL url) {
		double ewma = endpointStatsRegistry.getLatencyScore(url.getAddress());
		if (Double.isNaN(ewma) || ewma <= 0) {
			Integer weight = url.getWeight();
			if (weight != null && weight > 0) {
				return 1.0d / weight;
			}
			return Double.MAX_VALUE;
		}
		return ewma;
	}
}
