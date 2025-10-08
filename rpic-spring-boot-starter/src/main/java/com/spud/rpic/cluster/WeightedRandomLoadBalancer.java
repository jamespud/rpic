package com.spud.rpic.cluster;

import com.spud.rpic.model.ServiceURL;
import java.util.List;
import java.util.Random;

/**
 * @author Spud
 * @date 2025/2/27
 */
public class WeightedRandomLoadBalancer implements LoadBalancer {

	private final Random random = new Random();

	@Override
	public ServiceURL select(List<ServiceURL> urls) {
		if (urls == null || urls.isEmpty()) {
			return null;
		}

		int totalWeight = 0;
		for (ServiceURL url : urls) {
			totalWeight += url.getWeight();
		}

		int offset = random.nextInt(totalWeight);
		for (ServiceURL url : urls) {
			offset -= url.getWeight();
			if (offset < 0) {
				return url;
			}
		}

		return urls.get(0);
	}

	@Override
	public String getType() {
		return LoadBalancerType.WEIGHTED_RANDOM.getType();
	}
}