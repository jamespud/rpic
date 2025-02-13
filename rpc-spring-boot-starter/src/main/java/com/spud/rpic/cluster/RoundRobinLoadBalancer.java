package com.spud.rpic.cluster;

import com.spud.rpic.model.ServiceURL;

import java.util.List;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class RoundRobinLoadBalancer implements LoadBalancer {
    private int currentIndex = 0;
    @Override
    public ServiceURL select(List<ServiceURL> serviceURLs) {
        ServiceURL serviceURL = serviceURLs.get(currentIndex);
        currentIndex = (currentIndex + 1) % serviceURLs.size();
        return serviceURL;
    }
}