package com.spud.rpic.model;

import com.alibaba.nacos.api.naming.pojo.Instance;
import java.io.Serializable;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Data
@Builder
public class ServiceMetadata implements Serializable {

  /**
   * 服务接口类
   */
  private Class<?> interfaceClass;

  /**
   * 服务接口名称
   */
  private String interfaceName;

  /**
   * 服务版本
   */
  private String version;

  /**
   * 服务分组
   */
  private String group;

  /**
   * 应用名称
   */
  private String application;

  /**
   * 模块名称
   */
  private String module;

  /**
   * 服务提供者地址
   */
  private String host;

  /**
   * 服务提供者端口
   */
  private int port;

  /**
   * 服务提供者权重
   */
  private int weight;

  /**
   * 服务提供者配置
   */
  private String provider;

  /**
   * 服务消费者配置
   */
  private String consumer;

  /**
   * 协议配置
   */
  private String protocol;

  /**
   * 监控配置
   */
  private String monitor;

  /**
   * 注册中心配置
   */
  private String[] registry;

  private Map<String, String> parameters;

  public String getServiceKey() {
    // TODO:
    return getServiceId();
  }

  /**
   * 获取服务唯一标识
   */
  public String getServiceId() {
    StringBuilder sb = new StringBuilder();
    sb.append(interfaceName != null ? interfaceName : interfaceClass.getName())
        .append(":");
    if (version != null && !version.isEmpty()) {
      sb.append(version).append(":");
    }
    if (group != null && !group.isEmpty()) {
      sb.append(group);
    }
    return sb.toString();
  }

  // 转换为ServiceURL的方法
  public ServiceURL convertToServiceURL() {
    // 确保协议不为null，如果未设置则使用默认值
    String protocolToUse = protocol != null && !protocol.isEmpty() ? protocol : "rpic";
    return new ServiceURL(host, port, interfaceName, protocolToUse, group, version, weight, parameters);
  }

  public Instance convertToInstance() {
    Instance instance = new Instance();
    instance.setIp(host);
    instance.setPort(port);
    instance.setServiceName(interfaceName);
    instance.setMetadata(convertToServiceURL().getParameters());
    instance.setWeight(weight);
    return instance;
  }
}