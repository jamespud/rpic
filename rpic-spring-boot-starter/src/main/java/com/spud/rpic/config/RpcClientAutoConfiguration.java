package com.spud.rpic.config;

import com.spud.rpic.cluster.LoadBalancer;
import com.spud.rpic.cluster.LoadBalancerFactory;
import com.spud.rpic.config.bean.RpcReferenceAnnotationProcessor;
import com.spud.rpic.config.bean.ServiceStarter;
import com.spud.rpic.io.netty.client.ConnectionPool;
import com.spud.rpic.io.netty.client.NettyNetClient;
import com.spud.rpic.io.netty.client.RpcClientHandler;
import com.spud.rpic.io.netty.client.RpcClientInitializer;
import com.spud.rpic.io.netty.client.invocation.ClientInvocation;
import com.spud.rpic.io.netty.client.invocation.DefaultClientInvocation;
import com.spud.rpic.io.serializer.Serializer;
import com.spud.rpic.property.RpcProperties;
import com.spud.rpic.proxy.CglibProxyFactory;
import com.spud.rpic.proxy.ProxyFactory;
import com.spud.rpic.registry.Registry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author Spud
 * @date 2025/2/13
 */
@Configuration
@ConditionalOnProperty(prefix = "rpc", name = "role", havingValue = "client", matchIfMissing = true)
@EnableConfigurationProperties(RpcProperties.class)
@AutoConfigureAfter(RpcAutoConfiguration.class)
@Slf4j
public class RpcClientAutoConfiguration implements DisposableBean {

  @Bean
  @ConditionalOnMissingBean
  public RpcClientHandler rpcClientHandler(Serializer serializer) {
    return new RpcClientHandler(serializer);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(RpcClientHandler.class)
  public RpcClientInitializer rpcClientInitializer(RpcClientHandler rpcClientHandler) {
    boolean debugMode = Boolean.getBoolean("rpc.debug");
    return new RpcClientInitializer(rpcClientHandler, debugMode);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean({ RpcClientInitializer.class })
  public ConnectionPool connectionPool(RpcProperties properties,
      RpcClientInitializer rpcClientInitializer) {
    return new ConnectionPool(properties.getClient(), rpcClientInitializer);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean({ ConnectionPool.class, RpcClientHandler.class })
  public NettyNetClient rpcClient(ConnectionPool connectionPool,
      RpcClientHandler rpcClientHandler) {
    return new NettyNetClient(connectionPool, rpcClientHandler);
  }

  @Bean
  @ConditionalOnMissingBean
  public LoadBalancerFactory loadBalancerFactory() {
    return new LoadBalancerFactory();
  }

  @Bean
  @ConditionalOnMissingBean
  public LoadBalancer loadBalancer(RpcProperties properties, LoadBalancerFactory loadBalancerFactory) {
    return loadBalancerFactory.getLoadBalancer(properties.getClient().getLoadbalance());
  }

  @Bean
  @ConditionalOnMissingBean
  public ClientInvocation clientInvocation(Registry registry, LoadBalancer loadBalancer,
      NettyNetClient nettyNetClient) {
    return new DefaultClientInvocation(registry, loadBalancer, nettyNetClient, 5);
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
  @ConditionalOnMissingBean
  public ServiceStarter serviceStarter(Registry registry, RpcProperties rpcProperties) {
    log.info("Creating ServiceStarter bean for client role");
    return new ServiceStarter(registry, rpcProperties);
  }

  @Override
  public void destroy() throws Exception {

  }
}
