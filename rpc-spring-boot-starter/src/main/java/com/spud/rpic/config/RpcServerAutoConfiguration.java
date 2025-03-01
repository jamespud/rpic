package com.spud.rpic.config;

import com.spud.rpic.cluster.LoadBalancer;
import com.spud.rpic.io.netty.client.NettyNetClient;
import com.spud.rpic.io.netty.client.invocation.DefaultClientInvocation;
import com.spud.rpic.io.netty.client.invocation.ClientInvocation;
import com.spud.rpic.io.netty.server.NettyNetServer;
import com.spud.rpic.io.netty.server.RpcServerHandler;
import com.spud.rpic.io.netty.server.RpcServerInitializer;
import com.spud.rpic.io.netty.server.invocation.DefaultServerInvocation;
import com.spud.rpic.io.serializer.Serializer;
import com.spud.rpic.property.RpcProperties;
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
@AutoConfigureAfter(RpcAutoConfiguration.class)
public class RpcServerAutoConfiguration implements DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(RpcServerAutoConfiguration.class);

    private final RpcProperties rpcProperties;

    private final ApplicationContext applicationContext;

    public RpcServerAutoConfiguration(RpcProperties rpcProperties, ApplicationContext applicationContext) {
        this.rpcProperties = rpcProperties;
        this.applicationContext = applicationContext;
    }

    @Bean
    @ConditionalOnMissingBean
    public RpcServerHandler rpcServerHandler(Serializer serializer, DefaultServerInvocation defaultServerInvocation) {
        return new RpcServerHandler(serializer, defaultServerInvocation);
    }


    @Bean
    @ConditionalOnMissingBean
    public RpcServerInitializer rpcServerInitializer(RpcServerHandler rpcServerHandler) {
        return new RpcServerInitializer(rpcServerHandler);
    }

    @Bean
    @ConditionalOnMissingBean
    public NettyNetServer rpcServer(RpcProperties properties, RpcServerInitializer serverInitializer) {
        return new NettyNetServer(properties, serverInitializer);
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientInvocation clientInvocation(Registry registry, LoadBalancer loadBalancer, NettyNetClient nettyNetClient) {
        return new DefaultClientInvocation(registry, loadBalancer, nettyNetClient, 5);
    }

    @Bean
    public void rpcServerInitializer() {
        
    }

    @Override
    public void destroy() throws Exception {
        NettyNetServer nettyNetServer = applicationContext.getBean(NettyNetServer.class);
        if (nettyNetServer != null) {
            try {
                logger.info("Shutting down RPC server...");
                nettyNetServer.stop();
                logger.info("RPC server stopped successfully");
            } catch (Exception e) {
                logger.error("Error stopping RPC server", e);
            }
        }

        // 清理注册中心
        Registry registry = applicationContext.getBean(Registry.class);
        if (registry != null) {
            try {
                logger.info("Unregistering services...");
//                registry.unregisterAll();
                logger.info("Services unregistered successfully");
            } catch (Exception e) {
                logger.error("Error unregistering services", e);
            }
        }
    }

}
