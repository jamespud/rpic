package com.spud.rpic.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Data
@ConfigurationProperties(prefix = "rpc")
public class RpcProperties {
  
  private String role = "client";

  /**
   * 应用名称
   */
  private String applicationName = "rpc-application";

  /**
   * 序列化类型
   */
  private String serializeType = "json";

  /**
   * 压缩类型
   */
  private String compressType = "none";

  /**
   * 注册中心配置
   */
  private RegistryConfig registry = new RegistryConfig();

  @NestedConfigurationProperty
  private RpcServerProperties server = new RpcServerProperties();

  @NestedConfigurationProperty
  private RpcClientProperties client = new RpcClientProperties();

  @Data
  public static class RegistryConfig {

    /**
     * 注册中心类型(nacos, zookeeper)
     */
    private String type = "nacos";

    /**
     * 注册中心地址
     */
    private String address = "localhost:8848";

    /**
     * 注册超时时间
     */
    private int timeout = 3000;

    // 注册缓存配置
    private long registrationTtl = 30;  // 分钟
    private long registrationRefresh = 15;  // 分钟

    // 发现缓存配置
    private long discoveryTtl = 30;  // 秒
    private long discoveryRefresh = 15;  // 秒
  }
}