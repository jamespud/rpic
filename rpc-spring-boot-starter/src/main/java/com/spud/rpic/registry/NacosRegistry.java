package com.spud.rpic.registry;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.spud.rpic.config.RpcProperties;
import com.spud.rpic.model.ServiceMetadata;
import com.spud.rpic.model.ServiceURL;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class NacosRegistry extends Registry {

    private final NamingService namingService;

    public NacosRegistry(String registryAddress, RpcProperties properties) {
        super(properties.getCache().getRegistrationTtl(), properties.getCache().getRegistrationRefresh());
        try {
            namingService = NacosFactory.createNamingService(registryAddress);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Nacos registry", e);
        }
    }

    @Override
    public void register(ServiceMetadata metadata) {
        if (registrationCache.get(metadata, null) != null)
            return;
        try {
            // instance.getServiceName() = `serviceName`-`serviceVersion`
            Instance instance = metadata.toInstance();
            namingService.registerInstance(instance.getServiceName(), instance);
            registrationCache.put(metadata, true);
        } catch (Exception e) {
            registrationCache.invalidate(metadata);
            throw new RuntimeException("Failed to register service", e);
        }
    }

    @Override
    public List<ServiceURL> doDiscover(ServiceMetadata metadata) {
        List<ServiceURL> urls = new ArrayList<>();
        try {
            List<Instance> instances = namingService.getAllInstances(
                    metadata.getServiceName()
            );

            return instances.stream()
                    .map(this::convertToServiceURL)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to discover service", e);
        }
    }

    @Override
    public void subscribe(ServiceMetadata serviceMetadata, ServiceChangeListener listener) {
        try {
            namingService.subscribe(serviceMetadata.getServiceName(), new EventListener() {
                @Override
                public void onEvent(Event event) {
                    listener.serviceChanged(serviceMetadata.getServiceName(), refreshDiscover(serviceMetadata));
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to subscribe service", e);
        }
    }
    
    private ServiceURL convertToServiceURL(Instance instance) {
        String[] meta = instance.getServiceName().split("-");
        ServiceURL serviceURL = new ServiceURL(meta[0], instance.getServiceName(), instance.getIp() + instance.getPort(), meta[1], instance.getWeight());
        return serviceURL;
    }
}