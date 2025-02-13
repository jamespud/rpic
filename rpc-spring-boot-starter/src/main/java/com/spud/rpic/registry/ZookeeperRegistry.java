package com.spud.rpic.registry;

import com.spud.rpic.common.constants.ZooKeeperConstants;
import com.spud.rpic.config.RpcProperties;
import com.spud.rpic.model.ServiceMetadata;
import com.spud.rpic.model.ServiceURL;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Spud
 * @date 2025/2/9
 */

public class ZookeeperRegistry extends Registry {

    private CuratorFramework client;

    public ZookeeperRegistry(String registryAddress, RpcProperties properties) {
        super(properties.getCache().getRegistrationTtl(), properties.getCache().getRegistrationRefresh());
        client = CuratorFrameworkFactory.builder()
                .connectString(registryAddress)
                .sessionTimeoutMs(5000)
                .connectionTimeoutMs(5000)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        client.start();
    }

    /**
     * 注册服务
     * ZooKeeperConstants.ZK_REGISTRY_PATH + "/" + serviceMetadata.getServiceName() +"/" + serviceMetadata.getServiceVersion()
     * /services/com.spud.rpc.service.HelloService/1.0.0/instance-2/127.0.0.1/8080
     */
    @Override
    public void register(ServiceMetadata metadata) {
        if (registrationCache.get(metadata) != null) return;

        String servicePath = getServicePath(metadata);
        try {
            Stat stat = client.checkExists().forPath(servicePath);
            if (stat == null) {
                client.create().creatingParentsIfNeeded().forPath(servicePath);
            }
            // 使用临时顺序节点
            String instancePath = client.create().withMode(CreateMode.EPHEMERAL_SEQUENTIAL).forPath(servicePath + "/instance-");
            int id = Integer.parseInt(instancePath.substring(instancePath.lastIndexOf('-') + 1));
            //host:port:weight
            client.setData().forPath(instancePath, (metadata.getHost() + ":" + metadata.getPort() + ":" + metadata.getWeight()).getBytes());
            metadata.setServiceId(id);
            registrationCache.put(metadata, true);
        } catch (Exception e) {
            registrationCache.invalidate(metadata);
            throw new RuntimeException("Failed to register service", e);
        }
    }

    @Override
    public List<ServiceURL> doDiscover(ServiceMetadata metadata) {
        List<ServiceURL> urls = new ArrayList<>();
        String servicePath = getServicePath(metadata);
        try {
            List<String> instancePath = client.getChildren().forPath(servicePath);
            // path: /version/instance-id
            for (String path : instancePath) {
                String[] dirs = path.split("/");
                if (dirs.length != ZooKeeperConstants.SERVICE_PATH_LEN) {
                    continue;
                }
                byte[] data = client.getData().forPath(servicePath + "/" + path);
                // host:port:weight
                String[] parts = new String(data).split(":");
                String id = dirs[ZooKeeperConstants.SERVICE_PATH_LEN - 1];
                ServiceURL serviceURL = new ServiceURL(id, metadata.getServiceName(), parts[0] + ":" + parts[1], dirs[0], Double.parseDouble(parts[2]));
                urls.add(serviceURL);
            }
        } catch (Exception e) {
            discoveryCache.invalidate(metadata.getServiceName());
            throw new RuntimeException("Failed to discover service", e);
        }
        return urls;
    }

    @Override
    public void subscribe(ServiceMetadata serviceMetadata, ServiceChangeListener listener) {
        PathChildrenCache cache = new PathChildrenCache(
                client, getServicePath(serviceMetadata), true
        );

        cache.getListenable().addListener((client, event) -> {
            discoveryCache.invalidate(serviceMetadata.getServiceName());
            listener.serviceChanged(
                    serviceMetadata.getServiceName(),
                    discover(serviceMetadata)
            );
        });

        try {
            cache.start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to subscribe service", e);
        }
        
//        PathChildrenCache pathChildrenCache = new PathChildrenCache(client, getServicePath(serviceMetadata), true);
//        try {
//            pathChildrenCache.getListenable().addListener(new PathChildrenCacheListener() {
//                @Override
//                public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
//                    listener.serviceChanged(serviceMetadata.getServiceName(), discover(serviceMetadata));
//                }
//            });
//            pathChildrenCache.start();
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to subscribe service", e);
//        }
    }

    private String getServicePath(ServiceMetadata serviceMetadata) {
        return ZooKeeperConstants.ZK_REGISTRY_PATH + "/"
                + serviceMetadata.getServiceName() + "/"
                + serviceMetadata.getServiceVersion();
    }
}