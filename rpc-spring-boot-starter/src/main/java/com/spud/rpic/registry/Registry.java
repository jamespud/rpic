package com.spud.rpic.registry;

import com.spud.rpic.model.ServiceMetadata;
import com.spud.rpic.model.ServiceURL;
import com.spud.rpic.registry.cache.CaffeineCache;
import com.spud.rpic.registry.cache.RpcCache;
import org.springframework.beans.factory.DisposableBean;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Spud
 * @date 2025/2/9
 */
public abstract class Registry implements DisposableBean {

    protected final RpcCache<ServiceMetadata, Boolean> registrationCache;

    protected final RpcCache<String, List<ServiceURL>> discoveryCache;

    protected Registry(long ttl, long refreshInterval) {
        this.registrationCache = new CaffeineCache<>(builder ->
                builder.expireAfterWrite(ttl, TimeUnit.MINUTES)
                        .refreshAfterWrite(refreshInterval, TimeUnit.MINUTES)
        );
        this.discoveryCache = new CaffeineCache<>(builder ->
                builder.expireAfterWrite(ttl, TimeUnit.SECONDS)
                        .refreshAfterWrite(refreshInterval, TimeUnit.SECONDS)
        );
    }

    protected Registry(long discoveryTtl, long discoveryRefreshInterval, long registrationTtl, long registrationRefreshInterval) {
        this.registrationCache = new CaffeineCache<>(builder ->
                builder.expireAfterWrite(registrationTtl, TimeUnit.MINUTES)
                        .refreshAfterWrite(registrationRefreshInterval, TimeUnit.MINUTES)
        );
        this.discoveryCache = new CaffeineCache<>(builder ->
                builder.expireAfterWrite(discoveryTtl, TimeUnit.SECONDS)
                        .refreshAfterWrite(discoveryRefreshInterval, TimeUnit.SECONDS)
        );    }

    public abstract void register(ServiceMetadata serviceMetadata);

    public abstract void register(List<ServiceMetadata> serviceMetadata);

    public final List<ServiceURL> discover(ServiceMetadata metadata) {
        return discoveryCache.get(metadata.getServiceId(),
                k -> doDiscover(metadata)
        );
    }

    protected abstract List<ServiceURL> doDiscover(ServiceMetadata metadata);

    public abstract void subscribe(ServiceMetadata serviceMetadata, ServiceChangeListener listener);

    public abstract void subscribe(List<ServiceMetadata> serviceMetadata, ServiceChangeListener listener);

    public final List<ServiceURL> refreshDiscover(ServiceMetadata metadata) {
        return discoveryCache.get(metadata.getServiceId(),
                k -> doDiscover(metadata)
        );
    }

    public abstract void unregister(ServiceMetadata metadata);
}