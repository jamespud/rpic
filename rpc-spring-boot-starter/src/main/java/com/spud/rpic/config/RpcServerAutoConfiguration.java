package com.spud.rpic.config;

import com.spud.rpic.annotation.RpcService;
import com.spud.rpic.io.client.InvocationClient;
import com.spud.rpic.model.ServiceMetadata;
import com.spud.rpic.io.serializer.Serializer;
import com.spud.rpic.io.server.RpcServer;
import com.spud.rpic.io.server.ServerServiceInvocation;
import com.spud.rpic.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Configuration
@ConditionalOnProperty(prefix = "rpc", name = "role", havingValue = "server")
@EnableConfigurationProperties(RpcProperties.class)
@AutoConfigureAfter(RpcCoreAutoConfiguration.class)
public class RpcServerAutoConfiguration  implements DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(RpcServerAutoConfiguration.class);

    private final RpcProperties rpcProperties;

    private final ApplicationContext applicationContext;

    public RpcServerAutoConfiguration(RpcProperties rpcProperties, ApplicationContext applicationContext) {
        this.rpcProperties = rpcProperties;
        this.applicationContext = applicationContext;
    }
    @Bean
    @ConditionalOnMissingBean
    public ServerServiceInvocation serverServiceInvocation() {
        return new ServerServiceInvocation();
    }

    @Bean
    @ConditionalOnMissingBean
    public RpcServer rpcServer() {
        return new RpcServer(rpcProperties.getServerPort());
    }

    @Bean
    @ConditionalOnMissingBean
    public InvocationClient invocationClient(Serializer serializer) {
        return new InvocationClient(serializer);
    }

    @Bean
    public void rpcServerInitializer() {
        new Thread(() -> {
            RpcServer rpcServer = applicationContext.getBean(RpcServer.class);
            rpcServer.start();
        }).start();
        // 注册服务
        String[] beanNames = applicationContext.getBeanNamesForAnnotation(RpcService.class);
        Registry registry = applicationContext.getBean(Registry.class);
        for (String beanName : beanNames) {
            Object serviceBean = applicationContext.getBean(beanName);
            RpcService rpcService = serviceBean.getClass().getAnnotation(RpcService.class);
            String serviceName = rpcService.serviceName();
            if ("".equals(serviceName)) {
                serviceName = serviceBean.getClass().getName();
            }
            ServiceMetadata serviceMetadata =
                    new ServiceMetadata(serviceName);
            registry.register(serviceMetadata);
        }
    }

    @Bean
    public void rpcClientInitializer() {
        // TODO: 客户端初始化
    }

    @Override
    public void destroy() throws Exception {
        // TODO: 关闭RPC服务
    }
}
