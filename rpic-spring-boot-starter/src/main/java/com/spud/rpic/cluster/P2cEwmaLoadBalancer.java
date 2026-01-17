package com.spud.rpic.cluster;

import com.spud.rpic.model.ServiceURL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 基于P2C+EWMA的负载均衡实现。
 * <p>
 * P2C (Pick Two Choices) 算法通过随机选择两个节点并比较它们的 EWMA (Exponentially Weighted Moving Average)
 * 延迟来选择更好的节点，平衡了性能和随机性。
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

		int candidateSize = candidates.size();
		if (candidateSize == 1) {
			return candidates.get(0);
		}

		int index1 = ThreadLocalRandom.current().nextInt(candidateSize);
		int index2 = ThreadLocalRandom.current().nextInt(candidateSize);
		if (index1 == index2) {
			index2 = (index1 + 1) % candidateSize;
		}

		ServiceURL first = candidates.get(index1);
		ServiceURL second = candidates.get(index2);

		return better(first, second);
	}

	@Override
	public String getType() {
		return LoadBalancerType.P2C_EWMA.getType();
	}

	private ServiceURL better(ServiceURL a, ServiceURL b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}

		double scoreA = calculateScore(a);
		double scoreB = calculateScore(b);

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

	/**
	 * 计算服务的综合分数
	 * <p>
	 * 分数由两部分组成：
	 * 1. EWMA 延迟（主要指标）
	 * 2. 服务权重（当没有延迟数据时作为备用）
	 * <p>
	 * 分数越低表示服务质量越好
	 */
	private double calculateScore(ServiceURL url) {
		double ewma = endpointStatsRegistry.getLatencyScore(url.getAddress());
		if (!Double.isNaN(ewma) && ewma > 0) {
			return ewma;
		}

		Integer weight = url.getWeight();
		if (weight != null && weight > 0) {
			return 1.0d / weight;
		}

		return Double.MAX_VALUE;
	}
}
