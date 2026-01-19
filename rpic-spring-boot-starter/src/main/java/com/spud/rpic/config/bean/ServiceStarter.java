package com.spud.rpic.config.bean;

import static com.spud.rpic.util.CommonUtils.buildConsumerMetadata;
import static com.spud.rpic.util.CommonUtils.buildMethodServiceMetadata;
import static com.spud.rpic.util.CommonUtils.buildServiceMetadata;

import com.spud.rpic.annotation.RpcReference;
import com.spud.rpic.annotation.RpcService;
import com.spud.rpic.io.netty.server.NettyNetServer;
import com.spud.rpic.model.ServiceMetadata;
import com.spud.rpic.property.RpcProperties;
import com.spud.rpic.registry.DefaultServiceChangeListener;
import com.spud.rpic.registry.Registry;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

/**
 * @author Spud
 * @date 2025/2/15
 */
@Slf4j
@Component
public class ServiceStarter implements ApplicationListener<ContextRefreshedEvent> {

	private final Registry registry;
	private final RpcProperties rpcProperties;
	private volatile boolean started = false;
	private static final Object lock = new Object();

	// 应用角色常量
	private static final String ROLE_SERVER = "server";
	private static final String ROLE_CLIENT = "client";

	public ServiceStarter(Registry registry, RpcProperties rpcProperties) {
		this.registry = registry;
		this.rpcProperties = rpcProperties;
	}

	@Override
	public void onApplicationEvent(ContextRefreshedEvent event) {
		if (started || event.getApplicationContext().getParent() != null) {
			log.debug("ServiceStarter already started or is a child context, skipping initialization");
			return;
		}

		synchronized (lock) {
			if (started) {
				log.debug("ServiceStarter already started under lock, skipping initialization");
				return;
			}
			try {
				ApplicationContext context = event.getApplicationContext();
				String role = rpcProperties.getRole();
				log.info("ServiceStarter initializing with role: {}", role);

				// 判断角色，决定启动哪些组件
				if (ROLE_SERVER.equals(role)) {
					log.info("Starting RPC server with role: {}", role);
					// 服务端需要启动Netty服务器
					startNettyServer(context);
					// 服务端需要注册服务
					registerServices(context);
				} else if (ROLE_CLIENT.equals(role)) {
					log.info("Starting RPC client with role: {}", role);
					// 客户端不需要启动服务器，也不需要注册服务
				} else {
					log.warn("Unknown RPC role: {}. Expected 'server' or 'client'", role);
				}

				// 无论是客户端还是服务端，都需要订阅服务
				subscribeServices(context);

				started = true;
				log.info("RPC {} starter initialized successfully", role);
			} catch (Exception e) {
				log.error("Failed to start RPC services", e);
				throw new RuntimeException("Failed to start RPC services", e);
			}
		}
	}

	private void registerServices(ApplicationContext context) {
		try {
			List<ServiceMetadata> providers = new ArrayList<>();
			// 1. 扫描类级别的 @RpcService 注解
			scanClassLevelServices(context, providers);
			// 2. 扫描方法级别的 @RpcService 注解
			scanMethodLevelServices(context, providers);
			if (!providers.isEmpty()) {
				// 添加服务器地址信息
				String host = InetAddress.getLocalHost().getHostAddress();
				int port = rpcProperties.getServer().getPort();
				providers.forEach(metadata -> {
					metadata.setHost(host);
					metadata.setPort(port);
					// 设置协议信息，确保不为空
					if (metadata.getProtocol() == null || metadata.getProtocol().isEmpty()) {
						metadata.setProtocol("rpic");
					}
				});

				// 批量注册服务
				registry.register(providers);
				log.info("Registered {} RPC services", providers.size());
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to register RPC services", e);
		}
	}

	private void subscribeServices(ApplicationContext context) {
		try {
			List<ServiceMetadata> consumers = new ArrayList<>();

			// 1. 扫描字段上的 @RpcReference 注解
			scanFieldReferences(context, consumers);

			// 2. 扫描方法参数上的 @RpcReference 注解
			scanParameterReferences(context, consumers);

			if (!consumers.isEmpty()) {
				registry.subscribe(consumers, new DefaultServiceChangeListener());
				log.info("Subscribed to {} RPC services", consumers.size());
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to subscribe to RPC services", e);
		}
	}

	private void scanClassLevelServices(ApplicationContext context, List<ServiceMetadata> providers) {
		Map<String, Object> serviceBeans = context.getBeansWithAnnotation(RpcService.class);
		serviceBeans.forEach((beanName, bean) -> {
			RpcService annotation = bean.getClass().getAnnotation(RpcService.class);
			if (annotation != null) {
				providers.add(buildServiceMetadata(annotation, bean));
			}
		});
	}

	private void scanMethodLevelServices(ApplicationContext context,
		List<ServiceMetadata> providers) {
		String[] beanNames = context.getBeanDefinitionNames();
		for (String beanName : beanNames) {
			Object bean = context.getBean(beanName);
			Method[] methods = bean.getClass().getDeclaredMethods();
			for (Method method : methods) {
				RpcService annotation = method.getAnnotation(RpcService.class);
				if (annotation != null) {
					providers.add(buildMethodServiceMetadata(annotation, bean, method));
				}
			}
		}
	}

	private void scanFieldReferences(ApplicationContext context, List<ServiceMetadata> consumers) {
		String[] beanNames = context.getBeanDefinitionNames();
		for (String beanName : beanNames) {
			Object bean = context.getBean(beanName);
			ReflectionUtils.doWithFields(bean.getClass(), field -> {
				RpcReference reference = field.getAnnotation(RpcReference.class);
				if (reference != null) {
					consumers.add(buildConsumerMetadata(reference, field.getType()));
				}
			});
		}
	}

	private void scanParameterReferences(ApplicationContext context,
		List<ServiceMetadata> consumers) {
		String[] beanNames = context.getBeanDefinitionNames();
		for (String beanName : beanNames) {
			Object bean = context.getBean(beanName);
			Method[] methods = bean.getClass().getDeclaredMethods();
			for (Method method : methods) {
				Parameter[] parameters = method.getParameters();
				for (Parameter parameter : parameters) {
					RpcReference reference = parameter.getAnnotation(RpcReference.class);
					if (reference != null) {
						consumers.add(buildConsumerMetadata(reference, parameter.getType()));
					}
				}
			}
		}
	}

	private void startNettyServer(ApplicationContext context) {
		try {
			// 尝试获取NettyNetServer，如果不存在则返回null
			final NettyNetServer nettyNetServer;
			try {
				nettyNetServer = context.getBean(NettyNetServer.class);
			} catch (Exception e) {
				log.error(
					"Failed to get NettyNetServer bean. RPC server role is set but server components are not available.",
					e);
				throw new RuntimeException(
					"NettyNetServer bean not found. Make sure RPC server dependencies are properly configured.");
			}

			if (nettyNetServer == null) {
				log.error("NettyNetServer bean is null. Cannot start server.");
				return;
			}

			// 使用 CountDownLatch 等待服务器启动完成
			CountDownLatch latch = new CountDownLatch(1);
			new Thread(() -> {
				try {
					nettyNetServer.start();
				} catch (Exception e) {
					log.error("Failed to start Netty server", e);
				} finally {
					latch.countDown();
				}
			}, "netty-rpc-server").start();

			// 等待服务器启动,设置超时时间
			if (!latch.await(30, TimeUnit.SECONDS)) {
				throw new RuntimeException("Timeout waiting for RPC server to start");
			}
			log.info("Netty RPC server started successfully on port {}",
				rpcProperties.getServer().getPort());
		} catch (Exception e) {
			throw new RuntimeException("Failed to start Netty server", e);
		}
	}
}
