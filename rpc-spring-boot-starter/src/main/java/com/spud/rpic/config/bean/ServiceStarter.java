package com.spud.rpic.config.bean;

import com.spud.rpic.annotation.RpcReference;
import com.spud.rpic.annotation.RpcService;
import com.spud.rpic.io.netty.server.NettyNetServer;
import com.spud.rpic.model.ServiceMetadata;
import com.spud.rpic.property.RpcProperties;
import com.spud.rpic.registry.DefaultServiceChangeListener;
import com.spud.rpic.registry.Registry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.spud.rpic.util.CommonUtils.buildConsumerMetadata;
import static com.spud.rpic.util.CommonUtils.buildMethodServiceMetadata;
import static com.spud.rpic.util.CommonUtils.buildServiceMetadata;

/**
 * @author Spud
 * @date 2025/2/15
 */
@Slf4j
public class ServiceStarter implements ApplicationListener<ContextRefreshedEvent> {

    private final Registry registry;

    private final RpcProperties rpcProperties;

    private volatile boolean started = false;

    private static final Object lock = new Object();

    public ServiceStarter(Registry registry, RpcProperties rpcProperties) {
        this.registry = registry;
        this.rpcProperties = rpcProperties;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (started || event.getApplicationContext().getParent() != null) {
            return;
        }

        synchronized (lock) {
            if (started) {
                return;
            }
            try {
                ApplicationContext context = event.getApplicationContext();
                // 启动 Netty 服务器
                startNettyServer(context);
                // 获取所有带有 RpcService 注解的 Bean
                registerServices(context);
                // 订阅所有带 RpcReference 注解的 Bean
                subscribeService(context);
                started = true;
                log.info("RPC service starter initialized successfully");
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

    private void subscribeService(ApplicationContext context) {
        ArrayList<ServiceMetadata> consumers = new ArrayList<>();
        Map<String, Object> consumerBeans = context.getBeansWithAnnotation(RpcReference.class);
        consumerBeans.forEach((beanName, bean) -> {
            RpcReference annotation = bean.getClass().getAnnotation(RpcReference.class);
            ServiceMetadata metadata = buildConsumerMetadata(annotation, bean.getClass());
            consumers.add(metadata);
        });
        registry.subscribe(consumers, new DefaultServiceChangeListener());
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

    private void scanMethodLevelServices(ApplicationContext context, List<ServiceMetadata> providers) {
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

    private void scanParameterReferences(ApplicationContext context, List<ServiceMetadata> consumers) {
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
            NettyNetServer nettyNetServer = context.getBean(NettyNetServer.class);
            // 使用 CountDownLatch 等待服务器启动完成
            CountDownLatch latch = new CountDownLatch(1);
            new Thread(() -> {
                try {
                    nettyNetServer.start();
                    latch.countDown();
                } catch (Exception e) {
                    log.error("Failed to start Netty server", e);
                }
            }, "netty-rpc-server").start();

            // 等待服务器启动,设置超时时间
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new RuntimeException("Timeout waiting for RPC server to start");
            }
            log.info("Netty RPC server started successfully on port {}", rpcProperties.getServer().getPort());
        } catch (Exception e) {
            throw new RuntimeException("Failed to start Netty server", e);
        }
    }
}
