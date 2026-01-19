package com.spud.rpic.registry;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.spud.rpic.common.constants.ZooKeeperConstants;
import com.spud.rpic.model.ServiceMetadata;
import com.spud.rpic.model.ServiceURL;
import com.spud.rpic.property.RpcProperties;
import com.spud.rpic.util.NetUtils;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;

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
		super(properties.getRegistry().getRegistrationTtl(),
			properties.getRegistry().getRegistrationRefresh());
		client = CuratorFrameworkFactory.builder()
			.connectString(properties.getRegistry().getAddress())
			.sessionTimeoutMs(properties.getRegistry().getTimeout())
			.connectionTimeoutMs(properties.getRegistry().getTimeout())
			.retryPolicy(new ExponentialBackoffRetry(1000, 3))
			.build();

		this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(
			new ThreadFactoryBuilder().setNameFormat("zk-registry-heartbeat-%d").build());

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
	 * 注册服务 ZooKeeperConstants.ZK_REGISTRY_PATH + "/" + serviceMetadata.getServiceName() +"/" +
	 * serviceMetadata.getVersion()
	 * /services/com.spud.rpc.service.HelloService/1.0.0/instance-2/127.0.0.1/8080
	 */
	@Override
	public void register(ServiceMetadata metadata) {
		if (!running.get()) {
			throw new RuntimeException("Registry is not running");
		}

		// 验证ServiceMetadata
		validateServiceMetadata(metadata);

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
				log.info("Created service path: {}", servicePath);
			}

			// 创建实例节点（临时节点）
			// 检查节点是否已存在，如果存在则先删除
			if (client.checkExists().forPath(instancePath) != null) {
				log.warn("Instance path already exists, deleting it first: {}", instancePath);
				client.delete().forPath(instancePath);
			}

			client.create()
				.creatingParentsIfNeeded()
				.withMode(CreateMode.EPHEMERAL)
				.forPath(instancePath, data);

			registrationCache.put(metadata, true);
			log.info("Successfully registered service: {} at path: {}", metadata.getServiceKey(),
				instancePath);
		} catch (Exception e) {
			registrationCache.invalidate(metadata);
			throw new RuntimeException(
				"Failed to register service: " + metadata.getServiceKey() + ", error: " + e.getMessage(),
				e);
		}
	}

	@Override
	public void register(List<ServiceMetadata> serviceMetadata) {
		serviceMetadata.forEach(this::register);
	}

	@Override
	protected List<ServiceURL> doDiscover(ServiceMetadata metadata) {
		// 先验证元数据的有效性
		validateServiceMetadata(metadata);

		String servicePath = buildServicePath(metadata);
		try {
			// 首先检查路径是否存在
			if (client.checkExists().forPath(servicePath) == null) {
				log.debug("Service path does not exist: {}", servicePath);
				return Collections.emptyList();
			}

			List<String> children = client.getChildren().forPath(servicePath);
			if (children.isEmpty()) {
				log.debug("No instances found for service: {}", metadata.getServiceKey());
				return Collections.emptyList();
			}

			return children.stream()
				.map(child -> {
					try {
						String childPath = servicePath + "/" + child;
						// 检查节点是否仍然存在（可能在获取列表后被删除）
						if (client.checkExists().forPath(childPath) == null) {
							return null;
						}

						byte[] data = client.getData().forPath(childPath);
						if (data == null || data.length == 0) {
							log.warn("Empty data for instance: {}", childPath);
							return null;
						}

						return deserialize(data);
					} catch (Exception e) {
						log.error("Failed to get instance data: {}", child, e);
						return null;
					}
				})
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
		} catch (Exception e) {
			log.error("Failed to discover service: {} - {}", metadata.getServiceKey(), e.getMessage());
			return Collections.emptyList();
		}
	}

	@Override
	public void subscribe(ServiceMetadata metadata, ServiceChangeListener listener) {
		// 先验证元数据的有效性
		validateServiceMetadata(metadata);

		String servicePath = buildServicePath(metadata);
		try {
			// 首先检查服务路径是否存在
			if (client.checkExists().forPath(servicePath) == null) {
				// 路径不存在，创建一个空的持久节点
				try {
					client.create()
						.creatingParentsIfNeeded()
						.withMode(CreateMode.PERSISTENT)
						.forPath(servicePath);
					log.info("Created service path for subscription: {}", servicePath);
				} catch (Exception e) {
					log.warn("Failed to create service path: {}, might be created by another client",
						servicePath);
					// 忽略可能的并发创建异常
				}
			}

			// 检查是否已经有监听器
			if (watcherMap.containsKey(servicePath)) {
				log.debug("Already subscribed to service: {}", servicePath);
				// 立即通知当前实例列表
				List<ServiceURL> urls = doDiscover(metadata);
				listener.serviceChanged(metadata.getInterfaceName(), urls);
				return;
			}

			// 创建服务监听器，使用安全的方式处理事件
			CuratorCache cache = CuratorCache.builder(client, servicePath)
				.withExceptionHandler(e -> log.error("Error in curator cache for path: {}", servicePath, e))
				.build();

			// 注册监听器，使用try-catch捕获处理过程中的异常
			cache.listenable().addListener((type, oldData, newData) -> {
				try {
					List<ServiceURL> urls = doDiscover(metadata);
					listener.serviceChanged(metadata.getInterfaceName(), urls);
				} catch (Exception e) {
					log.error("Error handling service change event for: {}", metadata.getServiceKey(), e);
					// 不抛出异常，避免影响监听器线程
				}
			});

			// 安全启动监听器
			try {
				cache.start();
				log.info("Started watching service: {}", servicePath);
				watcherMap.put(servicePath, cache);
			} catch (Exception e) {
				log.error("Failed to start cache for path: {}", servicePath, e);
				cache.close();
				throw e;
			}

			// 立即通知当前实例列表
			try {
				List<ServiceURL> urls = doDiscover(metadata);
				listener.serviceChanged(metadata.getInterfaceName(), urls);
			} catch (Exception e) {
				log.error("Failed to notify initial service instances: {}", metadata.getServiceKey(), e);
				// 不阻止订阅成功
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to subscribe service: " + metadata.getServiceKey(), e);
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
			if (!running.get()) {
				return; // 如果registry已停止，不执行心跳
			}

			registrationCache.asMap().forEach((metadata, registered) -> {
				if (registered) {
					try {
						String instancePath = buildInstancePath(metadata);
						if (client.checkExists().forPath(instancePath) == null) {
							// 节点不存在，重新注册
							log.info("Service instance lost, re-registering: {}", metadata.getServiceKey());
							register(metadata);
						}
					} catch (Exception e) {
						if (running.get()) { // 只在registry仍在运行时记录错误
							log.error("Failed to check service registration: {} - {}",
								metadata.getServiceKey(), e.getMessage());
						}
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
		// 确保必要字段不为空
		if (metadata == null) {
			throw new IllegalArgumentException("ServiceMetadata cannot be null");
		}

		String interfaceName = metadata.getInterfaceName();
		String version = metadata.getVersion();

		// 使用默认值替代空值
		if (interfaceName == null || interfaceName.isEmpty()) {
			if (metadata.getInterfaceClass() != null) {
				interfaceName = metadata.getInterfaceClass().getName();
			} else {
				throw new IllegalArgumentException("Service interface name cannot be null or empty");
			}
		}

		if (version == null || version.isEmpty()) {
			version = "default";
			log.warn("Service version is empty, using 'default' for service: {}", interfaceName);
		}

		return String.format("%s/%s/%s", ZooKeeperConstants.ZK_REGISTRY_PATH, interfaceName, version);
	}

	private String buildInstancePath(ServiceMetadata metadata) {
		return String.format("%s/instance-%s", buildServicePath(metadata),
			buildInstanceId(metadata));
	}

	private String buildInstanceId(ServiceMetadata metadata) {
		return String.format("%s_%d", metadata.getHost(), metadata.getPort());
	}

	private byte[] serialize(ServiceMetadata metadata) {
		try {
			ServiceURL serviceURL = metadata.convertToServiceURL();
			String json = JacksonUtils.toJson(serviceURL);
			if (json == null) {
				throw new RuntimeException("Failed to serialize to JSON: " + metadata);
			}
			return json.getBytes(StandardCharsets.UTF_8);
		} catch (Exception e) {
			log.error("Serialization error for metadata: {}", metadata, e);
			throw new RuntimeException("Failed to serialize metadata: " + metadata.getServiceKey(), e);
		}
	}

	private ServiceURL deserialize(byte[] data) {
		if (data == null || data.length == 0) {
			return null;
		}

		final int previewMax = 200;
		String json = null;
		try {
			json = new String(data, StandardCharsets.UTF_8);
			ServiceURL serviceURL = JacksonUtils.toObj(json, ServiceURL.class);
			if (serviceURL == null) {
				String preview = json.length() > previewMax ? json.substring(0, previewMax) + "..." : json;
				throw new RuntimeException("Failed to deserialize to ServiceURL from JSON preview: " + preview);
			}
			return serviceURL;
		} catch (Exception e) {
			int len = data.length;
			int hash = java.util.Arrays.hashCode(data);
			String preview = json == null ? "" : (json.length() > previewMax ? json.substring(0, previewMax) + "..." : json);
			log.error("Deserialization error: dataLen={}, dataHash={}, jsonPreview={}", len, hash, preview, e);
			throw new RuntimeException("Failed to deserialize metadata", e);
		}
	}

	/**
	 * 验证ServiceMetadata对象的有效性
	 *
	 * @param metadata 需要验证的元数据对象
	 * @throws IllegalArgumentException 如果元数据对象无效
	 */
	private void validateServiceMetadata(ServiceMetadata metadata) {
		if (metadata == null) {
			throw new IllegalArgumentException("ServiceMetadata cannot be null");
		}

		// 检查并修复interfaceName
		if ((metadata.getInterfaceName() == null || metadata.getInterfaceName().isEmpty())) {
			if (metadata.getInterfaceClass() != null) {
				metadata.setInterfaceName(metadata.getInterfaceClass().getName());
			} else {
				throw new IllegalArgumentException(
					"Both interfaceName and interfaceClass cannot be null");
			}
		}

		// 确保interfaceClass有值
		if (metadata.getInterfaceClass() == null) {
			try {
				metadata.setInterfaceClass(Class.forName(metadata.getInterfaceName()));
			} catch (ClassNotFoundException e) {
				log.warn("Failed to load class for interface: {}", metadata.getInterfaceName());
				// 不强制抛出异常，允许只有接口名称的情况
			}
		}

		// 确保version有值
		if (metadata.getVersion() == null || metadata.getVersion().isEmpty()) {
			metadata.setVersion("1.0.0");
		}

		// 确保host和port有效（对于服务提供者）
		if (metadata.getHost() == null || metadata.getHost().isEmpty()) {
			// 可以获取本地IP地址
			metadata.setHost(NetUtils.getLocalHost());
		}

		// 确保weight有效
		if (metadata.getWeight() <= 0) {
			metadata.setWeight(1);
		}
	}
}