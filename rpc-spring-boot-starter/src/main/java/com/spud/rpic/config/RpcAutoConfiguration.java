package com.spud.rpic.config;

import com.spud.rpic.io.serializer.SerializerFactory;
import com.spud.rpic.property.RpcProperties;
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
    public SerializerFactory serializerFactory() {
        return new SerializerFactory();
    }
    
}