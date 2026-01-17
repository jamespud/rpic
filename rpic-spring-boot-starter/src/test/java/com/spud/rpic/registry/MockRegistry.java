package com.spud.rpic.registry;

import com.spud.rpic.model.ServiceMetadata;
import com.spud.rpic.model.ServiceURL;
import com.spud.rpic.property.RpcProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 测试用的模拟注册中心实现
 */
public class MockRegistry extends Registry {

    private final Map<ServiceMetadata, List<ServiceURL>> serviceRegistry = new ConcurrentHashMap<>();
    private final List<ServiceChangeListener> listeners = new ArrayList<>();

    public MockRegistry() {
        super(300, 60); // 调用父类构造器，设置TTL和刷新间隔
    }

    @Override
    public void register(ServiceMetadata serviceMetadata) {
        serviceRegistry.computeIfAbsent(serviceMetadata, k -> new ArrayList<>())
            .add(serviceMetadata.convertToServiceURL());
        notifyListeners(serviceMetadata);
    }

    @Override
    public void register(List<ServiceMetadata> serviceMetadata) {
        for (ServiceMetadata metadata : serviceMetadata) {
            register(metadata);
        }
    }

    @Override
    protected List<ServiceURL> doDiscover(ServiceMetadata metadata) {
        List<ServiceURL> urls = serviceRegistry.get(metadata);
        return urls != null ? new ArrayList<>(urls) : Collections.emptyList();
    }

    @Override
    public void subscribe(ServiceMetadata serviceMetadata, ServiceChangeListener listener) {
        listeners.add(listener);
    }

    @Override
    public void subscribe(List<ServiceMetadata> serviceMetadata, ServiceChangeListener listener) {
        for (ServiceMetadata metadata : serviceMetadata) {
            subscribe(metadata, listener);
        }
    }

    @Override
    public void unregister(ServiceMetadata metadata) {
        serviceRegistry.remove(metadata);
        notifyListeners(metadata);
    }

    @Override
    public void destroy() throws Exception {
        // 模拟销毁
    }

    /**
     * 添加测试服务实例
     */
    public void addTestService(ServiceMetadata metadata, ServiceURL url) {
        serviceRegistry.computeIfAbsent(metadata, k -> new ArrayList<>())
            .add(url);
        notifyListeners(metadata);
    }

    /**
     * 移除测试服务实例
     */
    public void removeTestService(ServiceMetadata metadata, ServiceURL url) {
        List<ServiceURL> urls = serviceRegistry.get(metadata);
        if (urls != null) {
            urls.remove(url);
            if (urls.isEmpty()) {
                serviceRegistry.remove(metadata);
            }
        }
        notifyListeners(metadata);
    }

    /**
     * 清空注册中心
     */
    public void clear() {
        serviceRegistry.clear();
        listeners.clear();
    }

    private void notifyListeners(ServiceMetadata metadata) {
        List<ServiceURL> urls = doDiscover(metadata);
        for (ServiceChangeListener listener : listeners) {
            listener.serviceChanged(metadata.getInterfaceName(), urls);
        }
    }
}
