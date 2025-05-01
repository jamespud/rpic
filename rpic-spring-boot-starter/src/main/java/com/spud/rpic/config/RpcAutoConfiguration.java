package com.spud.rpic.config;

import com.spud.rpic.io.serializer.Serializer;
import com.spud.rpic.io.serializer.SerializerFactory;
import com.spud.rpic.property.RpcProperties;
import com.spud.rpic.registry.NacosRegistry;
import com.spud.rpic.registry.Registry;
import com.spud.rpic.registry.ZookeeperRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Spud
 * @date 2025/2/13
 */
@Configuration
@EnableConfigurationProperties(RpcProperties.class)
public class RpcAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public SerializerFactory serializerFactory() {
    return new SerializerFactory();
  }

  @Bean
  @ConditionalOnMissingBean
  public Serializer serializer(RpcProperties rpcProperties) {
    return serializerFactory().getSerializer(rpcProperties.getSerializeType());
  }

  @Bean
  @ConditionalOnMissingBean
  public Registry registry(RpcProperties rpcProperties) {
    String registryType = rpcProperties.getRegistry().getType().toUpperCase();
    if (registryType.equals("NACOS")) {
      return new NacosRegistry(rpcProperties);
    } else if (registryType.equals("ZOOKEEPER")) {
      return new ZookeeperRegistry(rpcProperties);
    } else {
      throw new IllegalArgumentException("Unsupported registry type: " + registryType);
    }
  }

}