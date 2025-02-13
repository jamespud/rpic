package com.spud.rpic.config;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author Spud
 * @date 2025/2/13
 */
@Configuration
@ConditionalOnProperty(prefix = "rpc", name = "role", havingValue = "client")
@EnableConfigurationProperties(RpcProperties.class)
@AutoConfigureAfter(RpcCoreAutoConfiguration.class)
public class RpcClientAutoConfiguration implements DisposableBean {

    @Override
    public void destroy() throws Exception {
        
    }
}
