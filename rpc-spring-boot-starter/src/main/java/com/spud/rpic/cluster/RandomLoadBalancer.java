package com.spud.rpic.cluster;

import com.spud.rpic.model.ServiceURL;

import java.util.List;
import java.util.Random;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class RandomLoadBalancer implements LoadBalancer {
    @Override
    public ServiceURL select(List<ServiceURL> serviceURLs) {
        Random random = new Random();
        int index = random.nextInt(serviceURLs.size());
        return serviceURLs.get(index);
    }
}