package com.spud.rpic.registry;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.spud.rpic.common.constants.ZooKeeperConstants;
import com.spud.rpic.model.ServiceMetadata;
import com.spud.rpic.model.ServiceURL;
import com.spud.rpic.property.RpcProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Slf4j
public class ZookeeperRegistry extends Registry {

    private final CuratorFramework client;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, CuratorCache> watcherMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor;

    public ZookeeperRegistry(RpcProperties properties) {
        super(properties.getRegistry().getRegistrationTtl(), properties.getRegistry().getRegistrationRefresh());
        client = CuratorFrameworkFactory.builder()
                .connectString(properties.getRegistry().getAddress())
                .sessionTimeoutMs(properties.getRegistry().getTimeout())
                .connectionTimeoutMs(properties.getRegistry().getTimeout())
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();

        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder().setNameFormat("zk-registry-heartbeat-%d").build()
        );

        try {
            client.start();
            // 等待连接建立
            if (!client.blockUntilConnected(10, TimeUnit.SECONDS)) {
                throw new RuntimeException("Failed to connect to ZooKeeper");
            }
            // 确保根节点存在
            createRootPathIfNeeded();
            this.running.set(true);
            startHeartbeat();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ZooKeeper registry", e);
        }
    }

    private void createRootPathIfNeeded() throws Exception {
        try {
            if (client.checkExists().forPath(ZooKeeperConstants.ZK_REGISTRY_PATH) == null) {
                client.create()
                        .creatingParentsIfNeeded()
                        .withMode(CreateMode.PERSISTENT)
                        .forPath(ZooKeeperConstants.ZK_REGISTRY_PATH);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create root path", e);
        }
    }

    /**
     * 注册服务
     * ZooKeeperConstants.ZK_REGISTRY_PATH + "/" + serviceMetadata.getServiceName() +"/" + serviceMetadata.getVersion()
     * /services/com.spud.rpc.service.HelloService/1.0.0/instance-2/127.0.0.1/8080
     */
    @Override
    public void register(ServiceMetadata metadata) {
        if (!running.get()) {
            throw new RuntimeException("Registry is not running");
        }

        String servicePath = buildServicePath(metadata);
        String instancePath = buildInstancePath(metadata);
        byte[] data = serialize(metadata);

        try {
            // 创建服务节点（持久节点）
            if (client.checkExists().forPath(servicePath) == null) {
                client.create()
                        .creatingParentsIfNeeded()
                        .withMode(CreateMode.PERSISTENT)
                        .forPath(servicePath);
            }

            // 创建实例节点（临时节点）
            client.create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.EPHEMERAL)
                    .forPath(instancePath, data);

            registrationCache.put(metadata, true);
            log.info("Successfully registered service: {}", metadata);
        } catch (Exception e) {
            registrationCache.invalidate(metadata);
            throw new RuntimeException("Failed to register service: " + metadata, e);
        }
    }

    @Override
    public void register(List<ServiceMetadata> serviceMetadata) {
        serviceMetadata.forEach(this::register);
    }

    @Override
    protected List<ServiceURL> doDiscover(ServiceMetadata metadata) {
        String servicePath = buildServicePath(metadata);
        try {
            List<String> children = client.getChildren().forPath(servicePath);
            return children.stream()
                    .map(child -> {
                        try {
                            byte[] data = client.getData()
                                    .forPath(servicePath + "/" + child);
                            return deserialize(data);
                        } catch (Exception e) {
                            log.error("Failed to get instance data: {}", child, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to discover service: {}", metadata, e);
            return Collections.emptyList();
        }
    }

    @Override
    public void subscribe(ServiceMetadata metadata, ServiceChangeListener listener) {
        String servicePath = buildServicePath(metadata);
        try {
            // 创建服务监听器
            CuratorCache cache = CuratorCache.build(client, servicePath);

            // 注册监听器
            cache.listenable().addListener((type, oldData, newData) -> {
                List<ServiceURL> urls = doDiscover(metadata);
                listener.serviceChanged(metadata.getInterfaceName(), urls);
            });

            cache.start();
            watcherMap.put(servicePath, cache);

            // 立即通知当前实例列表
            List<ServiceURL> urls = doDiscover(metadata);
            listener.serviceChanged(metadata.getInterfaceName(), urls);
        } catch (Exception e) {
            throw new RuntimeException("Failed to subscribe service: " + metadata, e);
        }
    }

    @Override
    public void subscribe(List<ServiceMetadata> metadatas, ServiceChangeListener listener) {
        metadatas.forEach(metadata -> subscribe(metadata, listener));
    }

    @Override
    public void unregister(ServiceMetadata metadata) {
        try {
            String instancePath = buildInstancePath(metadata);
            client.delete().quietly().forPath(instancePath);
            registrationCache.invalidate(metadata);
            log.info("Successfully unregistered service: {}", metadata);
        } catch (Exception e) {
            throw new RuntimeException("Failed to unregister service: " + metadata, e);
        }
    }

    private void startHeartbeat() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            registrationCache.asMap().forEach((metadata, registered) -> {
                if (registered) {
                    try {
                        String instancePath = buildInstancePath(metadata);
                        if (client.checkExists().forPath(instancePath) == null) {
                            // 节点不存在，重新注册
                            register(metadata);
                        }
                    } catch (Exception e) {
                        log.error("Failed to check service registration: {}", metadata, e);
                    }
                }
            });
        }, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    public void destroy() {
        if (running.compareAndSet(true, false)) {
            try {
                // 关闭所有监听器
                watcherMap.values().forEach(CuratorCache::close);
                watcherMap.clear();

                // 注销所有服务
                registrationCache.asMap().keySet().forEach(this::unregister);

                // 关闭心跳执行器
                heartbeatExecutor.shutdown();
                if (!heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    heartbeatExecutor.shutdownNow();
                }

                // 关闭 Curator 客户端
                client.close();

                // 清理缓存
                registrationCache.invalidateAll();
                discoveryCache.invalidateAll();

            } catch (Exception e) {
                log.error("Error while destroying registry", e);
            }
        }
    }

    private String buildServicePath(ServiceMetadata metadata) {
        return String.format("%s/%s/%s", ZooKeeperConstants.ZK_REGISTRY_PATH, metadata.getInterfaceName(), metadata.getVersion());
    }

    private String buildInstancePath(ServiceMetadata metadata) {
        return String.format("%s/instance-", buildServicePath(metadata),
                buildInstanceId(metadata));
    }

    private String buildInstanceId(ServiceMetadata metadata) {
        return String.format("%s_%d", metadata.getHost(), metadata.getPort());
    }

    private byte[] serialize(ServiceMetadata metadata) {
        try {
            return JacksonUtils.toJson(metadata.convertToServiceURL()).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize metadata", e);
        }
    }

    private ServiceURL deserialize(byte[] data) {
        try {
            return JacksonUtils.toObj(new String(data, StandardCharsets.UTF_8), ServiceURL.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize metadata", e);
        }
    }
}