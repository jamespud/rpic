package com.spud.rpic.model;

import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.Data;

import java.io.Serializable;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Data
public class ServiceMetadata implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private int serviceId;
    
    private String serviceName;
    
    private Class<?> serviceInterface;
    
    private String serviceVersion;
    
    private String host;
    
    private int port;
    
    private double weight = 1.0;
    
    private String group;

    public ServiceMetadata(String serviceName) {
        this.serviceName = serviceName;
        try {
            this.serviceInterface = Class.forName(serviceName);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Service interface not found", e);
        }
    }
    
    public Instance toInstance() {
        Instance instance = new Instance();
        instance.setInstanceId(String.valueOf(serviceId));
        instance.setIp(host);
        instance.setPort(port);
        instance.setWeight(weight);
        instance.setServiceName(serviceName + "-" + serviceVersion);
        return instance;
    }
    
}