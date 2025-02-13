package com.spud.rpic.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Data
@ConfigurationProperties(prefix = "rpc")
public class RpcProperties {
    
    private int serverPort = 8088;
    
    private String registry = "zookeeper";
    
    private String registryAddress = "127.0.0.1:2181";
    
    private String serializer = "java";
    
    private String loadBalancer = "random";

    private CacheConfig cache = new CacheConfig();

    @Data
    public static class CacheConfig {
        private int registrationTtl = 10;     // 注册缓存保持时间（分钟）
        private int registrationRefresh = 5;  // 注册缓存刷新间隔（分钟）
        private int discoveryTtl = 30;        // 发现缓存TTL（秒）
        private int discoveryRefresh = 15;    // 发现缓存刷新间隔（秒）
        private int maxSize = 1000;           // 最大缓存服务数量
    }
}
