package com.spud.rpic.cluster;

import lombok.Getter;

/**
 * @author Spud
 * @date 2025/2/27
 */
@Getter
public enum LoadBalancerType {
    RANDOM("random"),
    ROUND_ROBIN("roundRobin"),
    WEIGHTED_RANDOM("weightedRandom"),
    WEIGHTED_ROUND_ROBIN("weightedRoundRobin"),
    LEAST_ACTIVE("leastActive"),
    CONSISTENT_HASH("consistentHash");

    private final String type;

    LoadBalancerType(String type) {
        this.type = type;
    }

    public static LoadBalancerType fromString(String type) {
        for (LoadBalancerType loadBalancerType : values()) {
            if (loadBalancerType.type.equalsIgnoreCase(type)) {
                return loadBalancerType;
            }
        }
        throw new IllegalArgumentException("Unknown load balancer type: " + type);
    }
}