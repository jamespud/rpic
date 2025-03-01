package com.spud.rpic.config;

import com.spud.rpic.config.bean.RpcReferenceAnnotationProcessor;
import com.spud.rpic.io.netty.client.ConnectionPool;
import com.spud.rpic.io.netty.client.NettyNetClient;
import com.spud.rpic.io.netty.client.RpcClientHandler;
import com.spud.rpic.io.netty.client.RpcClientInitializer;
import com.spud.rpic.io.netty.client.invocation.ClientInvocation;
import com.spud.rpic.io.serializer.Serializer;
import com.spud.rpic.property.RpcClientProperties;
import com.spud.rpic.property.RpcProperties;
import com.spud.rpic.proxy.CglibProxyFactory;
import com.spud.rpic.proxy.ProxyFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Spud
 * @date 2025/2/13
 */
@Configuration
@ConditionalOnProperty(prefix = "rpc", name = "role", havingValue = "client")
@EnableConfigurationProperties(RpcProperties.class)
@AutoConfigureAfter(RpcAutoConfiguration.class)
public class RpcClientAutoConfiguration implements DisposableBean {

    @Bean
    @ConditionalOnMissingBean
    public RpcClientHandler rpcClientHandler(Serializer serializer) {
        return new RpcClientHandler(serializer);
    }

    @Bean
    @ConditionalOnMissingBean
    public RpcClientInitializer rpcClientInitializer(RpcClientHandler rpcClientHandler) {
        return new RpcClientInitializer(rpcClientHandler);
    }

    @Bean
    @ConditionalOnMissingBean
    public ConnectionPool connectionPool(RpcClientProperties clientProperties,RpcClientInitializer rpcClientInitializer) {
        return new ConnectionPool(clientProperties, rpcClientInitializer);
    }

    @Bean
    @ConditionalOnMissingBean
    public NettyNetClient rpcClient(ConnectionPool connectionPool, RpcClientHandler rpcClientHandler ) {
        return new NettyNetClient(connectionPool, rpcClientHandler);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProxyFactory proxyFactory(ClientInvocation clientInvocation) {
        return new CglibProxyFactory(clientInvocation);
    }

    @Bean
    @ConditionalOnMissingBean
    public RpcReferenceAnnotationProcessor referenceInjector(ProxyFactory factory) {
        return new RpcReferenceAnnotationProcessor(factory);
    }

    @Bean
    @Override
    public void destroy() throws Exception {

    }
}
