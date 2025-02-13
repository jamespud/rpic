package com.spud.rpic.cluster;

import com.spud.rpic.model.ServiceURL;

import java.util.List;

/**
 * @author Spud
 * @date 2025/2/9
 */
public interface LoadBalancer {
    ServiceURL select(List<ServiceURL> serviceURLs);
}