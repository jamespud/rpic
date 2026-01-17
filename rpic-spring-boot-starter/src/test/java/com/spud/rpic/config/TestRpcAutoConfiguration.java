package com.spud.rpic.config;

import com.spud.rpic.io.serializer.Serializer;
import com.spud.rpic.io.serializer.SerializerFactory;
import com.spud.rpic.metrics.RpcMetricsRecorder;
import com.spud.rpic.metrics.RpcTracer;
import com.spud.rpic.property.RpcProperties;
import com.spud.rpic.registry.MockRegistry;
import com.spud.rpic.registry.Registry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 测试用的 RPC 自动配置，使用模拟组件替代真实实现
 */
@Configuration
@EnableConfigurationProperties(RpcProperties.class)
public class TestRpcAutoConfiguration {

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
    @Primary
    @ConditionalOnMissingBean
    public RpcMetricsRecorder rpcMetricsRecorder(RpcProperties rpcProperties) {
        return RpcMetricsRecorder.create(new SimpleMeterRegistry(), rpcProperties.getMetrics());
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean
    public Registry registry(RpcProperties rpcProperties) {
        return new MockRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public RpcTracer rpcTracer() {
        return RpcTracer.create();
    }

    @Bean
    public MockRegistry mockRegistry() {
        return new MockRegistry();
    }

    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}
