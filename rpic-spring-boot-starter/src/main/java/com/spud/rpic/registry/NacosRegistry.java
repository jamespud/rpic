package com.spud.rpic.registry;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.spud.rpic.model.ServiceMetadata;
import com.spud.rpic.model.ServiceURL;
import com.spud.rpic.property.RpcProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
public class NacosRegistry extends Registry {

	private final NamingService namingService;
	private final String serverAddr;
	private final Map<String, EventListener> listeners = new ConcurrentHashMap<>();
	private final AtomicBoolean running = new AtomicBoolean(false);
	private final ScheduledExecutorService heartbeatExecutor;

	public NacosRegistry(RpcProperties properties) {
		super(properties.getRegistry().getRegistrationTtl(),
			properties.getRegistry().getRegistrationRefresh());

		this.serverAddr = properties.getRegistry().getAddress();
		this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(
			new ThreadFactoryBuilder().setNameFormat("nacos-registry-heartbeat-%d").build()
		);

		try {
			Properties nacosProps = new Properties();
			nacosProps.setProperty("serverAddr", serverAddr);
			// 可以从properties添加更多nacos配置

			this.namingService = NacosFactory.createNamingService(nacosProps);
			this.running.set(true);
			startHeartbeat();
		} catch (Exception e) {
			throw new RuntimeException("Failed to initialize Nacos registry", e);
		}
	}

	@Override
	public void register(ServiceMetadata metadata) {
		if (!running.get()) {
			throw new RuntimeException("Registry is not running");
		}
		try {
			// 构建Nacos实例
			Instance instance = metadata.convertToInstance();
			// 注册服务
			namingService.registerInstance(instance.getServiceName(), instance);
			// 更新缓存
			registrationCache.put(metadata, true);

			log.info("Successfully registered service: {}", metadata);
		} catch (Exception e) {
			registrationCache.invalidate(metadata);
			throw new RuntimeException("Failed to register service: " + metadata, e);
		}
	}

	@Override
	public void register(List<ServiceMetadata> metadataList) {
		metadataList.forEach(this::register);
	}

	@Override
	public List<ServiceURL> doDiscover(ServiceMetadata metadata) {
		List<ServiceURL> urls = new ArrayList<>();
		try {
			List<Instance> instances = namingService.getAllInstances(metadata.getInterfaceName(), true);

			return convertToServiceURLs(instances);
		} catch (Exception e) {
			throw new RuntimeException("Failed to discover service", e);
		}
	}

	@Override
	public void subscribe(ServiceMetadata serviceMetadata, ServiceChangeListener listener) {
		try {
			String serviceName = serviceMetadata.getInterfaceName();
			EventListener nacosListener = event -> {
				listener.serviceChanged(serviceMetadata.getInterfaceName(),
					refreshDiscover(serviceMetadata));
			};

			namingService.subscribe(serviceName, nacosListener);
			listeners.put(serviceName, nacosListener);

			List<Instance> instances = namingService.selectInstances(serviceName, true);
			listener.serviceChanged(serviceMetadata.getInterfaceName(), refreshDiscover(serviceMetadata));
		} catch (Exception e) {
			throw new RuntimeException("Failed to subscribe service", e);
		}
	}

	@Override
	public void subscribe(List<ServiceMetadata> metadataList, ServiceChangeListener listener) {
		metadataList.forEach(metadata -> subscribe(metadata, listener));
	}

	@Override
	public void unregister(ServiceMetadata metadata) {
		try {
			Instance instance = metadata.convertToInstance();
			namingService.deregisterInstance(instance.getServiceName(), instance);
			registrationCache.invalidate(metadata);
			log.info("Successfully unregistered service: {}", metadata);
		} catch (Exception e) {
			throw new RuntimeException("Failed to unregister service: " + metadata, e);
		}
	}

	private void startHeartbeat() {
		heartbeatExecutor.scheduleAtFixedRate(() -> {
			try {
				// 检查所有已注册的服务
				registrationCache.asMap().forEach((metadata, registered) -> {
					if (registered) {
						try {
							// 刷新服务注册
							register(metadata);
						} catch (Exception e) {
							log.error("Failed to refresh service registration: {}", metadata, e);
						}
					}
				});
			} catch (Exception e) {
				log.error("Error in registry heartbeat", e);
			}
		}, 30, 30, TimeUnit.SECONDS);
	}

	@Override
	public void destroy() {
		if (running.compareAndSet(true, false)) {
			try {
				// 取消所有订阅
				listeners.forEach((serviceName, listener) -> {
					try {
						namingService.unsubscribe(serviceName, listener);
					} catch (Exception e) {
						log.error("Failed to unsubscribe service: {}", serviceName, e);
					}
				});

				// 注销所有服务
				registrationCache.asMap().keySet().forEach(this::unregister);

				// 关闭心跳执行器
				heartbeatExecutor.shutdown();
				if (!heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
					heartbeatExecutor.shutdownNow();
				}

				// 关闭Nacos客户端
				namingService.shutDown();

				// 清理缓存
				registrationCache.invalidateAll();
				discoveryCache.invalidateAll();
				listeners.clear();

			} catch (Exception e) {
				log.error("Error while destroying registry", e);
			}
		}
	}

	private List<ServiceURL> convertToServiceURLs(List<Instance> instances) {
		return instances.stream()
			.filter(Instance::isHealthy)
			.map(this::convertToServiceURL)
			.collect(Collectors.toList());
	}

	private ServiceURL convertToServiceURL(Instance instance) {
		String[] meta = instance.getServiceName().split("-");
		ServiceURL serviceURL = new ServiceURL(instance.getIp(), instance.getPort(), meta[0], meta[1],
			meta[2], "", (int) instance.getWeight(), null);
		return serviceURL;
	}
}