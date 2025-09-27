package com.spud.rpic.cluster;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Spud
 * @date 2025/2/27
 */
@Component
public class LoadBalancerFactory {

	private final Map<String, LoadBalancer> loadBalancerMap = new HashMap<>();

	public LoadBalancerFactory() {
		addLoadBalancer(new RandomLoadBalancer());
		addLoadBalancer(new RoundRobinLoadBalancer());
		addLoadBalancer(new WeightedRandomLoadBalancer());
	}

	public void addLoadBalancer(LoadBalancer loadBalancer) {
		loadBalancerMap.put(loadBalancer.getType(), loadBalancer);
	}

	public LoadBalancer getLoadBalancer(String type) {
		LoadBalancer loadBalancer = loadBalancerMap.get(type);
		if (loadBalancer == null) {
			return loadBalancerMap.get(LoadBalancerType.RANDOM.getType());
		}
		return loadBalancer;
	}
}