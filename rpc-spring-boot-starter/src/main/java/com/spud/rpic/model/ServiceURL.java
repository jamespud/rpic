package com.spud.rpic.model;

import lombok.Data;

/**
 * @author Spud
 * @date 2024/10/13
 */
@Data
public class ServiceURL {

    private String serviceId;

    private String serviceName;

    private String serviceAddress;

    private String serviceVersion;

    private double weight;

    // TODO: 多序列化支持

    public ServiceURL(String id, String serviceName, String serviceAddress, String serviceVersion, double weight) {
        this.serviceId = id;
        this.serviceName = serviceName;
        this.serviceAddress = serviceAddress;
        this.serviceVersion = serviceVersion;
        this.weight = weight;
    }
}
