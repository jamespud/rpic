package com.spud.rpic.config;

import com.spud.rpic.cluster.LoadBalancer;
import com.spud.rpic.cluster.RandomLoadBalancer;
import com.spud.rpic.cluster.RoundRobinLoadBalancer;
import com.spud.rpic.io.serializer.JavaSerializer;
import com.spud.rpic.io.serializer.ProtobufSerializer;
import com.spud.rpic.io.serializer.Serializer;
import com.spud.rpic.registry.NacosRegistry;
import com.spud.rpic.registry.Registry;
import com.spud.rpic.registry.ZookeeperRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Spud
 * @date 2025/2/13
 */
@Configuration
@EnableConfigurationProperties(RpcProperties.class)
public class RpcCoreAutoConfiguration {

    private final RpcProperties rpcProperties;

    private final ApplicationContext applicationContext;

    public RpcCoreAutoConfiguration(RpcProperties rpcProperties, ApplicationContext applicationContext) {
        this.rpcProperties = rpcProperties;
        this.applicationContext = applicationContext;
    }

    @Bean
    @ConditionalOnMissingBean
    public LoadBalancer loadBalancer() {
        switch (rpcProperties.getLoadBalancer()) {
            case "roundRobin":
                return new RoundRobinLoadBalancer();
            case "random":
                return new RandomLoadBalancer();
            default:
                throw new IllegalArgumentException("Unsupported load balancer: " + rpcProperties.getLoadBalancer());
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public Registry registry() {
        String registryType = rpcProperties.getRegistry().toLowerCase();
        String registryAddress = rpcProperties.getRegistryAddress();
        switch (registryType) {
            case "nacos":
                return new NacosRegistry(registryAddress, rpcProperties);
            case "zookeeper":
                return new ZookeeperRegistry(registryAddress, rpcProperties);
            default:
                throw new IllegalArgumentException("Unsupported registry type: " + registryType);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public Serializer serializer() {
        switch (rpcProperties.getSerializer()) {
            case "protobuf":
                return new ProtobufSerializer();
            case "java":
                return new JavaSerializer();
            case "hessian":
                // TODO: 实现Hessian序列化器
                throw new UnsupportedOperationException("Hessian serializer is not implemented yet");
            default:
                throw new IllegalArgumentException("Unsupported serializer: " + rpcProperties.getSerializer());
        }
    }
}
