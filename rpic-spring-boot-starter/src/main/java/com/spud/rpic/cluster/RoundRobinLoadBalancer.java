package com.spud.rpic.cluster;

import com.spud.rpic.model.ServiceURL;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class RoundRobinLoadBalancer implements LoadBalancer {

	private final AtomicInteger index = new AtomicInteger(0);

	@Override
	public ServiceURL select(List<ServiceURL> urls) {
		if (urls == null || urls.isEmpty()) {
			return null;
		}
		return urls.get(incrementAndGetModulo(urls.size()));
	}

	private int incrementAndGetModulo(int modulo) {
		for (; ; ) {
			int current = index.get();
			int next = (current + 1) % modulo;
			if (index.compareAndSet(current, next)) {
				return next;
			}
		}
	}

	@Override
	public String getType() {
		return LoadBalancerType.ROUND_ROBIN.getType();
	}
}