package com.spud.rpic.model;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Data;

/**
 * @author Spud
 * @date 2024/10/13
 */
@Data
public class ServiceURL implements Serializable {

  private static final long serialVersionUID = -1L;

  /**
   * 主机地址
   */
  private String host;

  /**
   * 端口号
   */
  private int port;

  private transient String serviceKey;

  /**
   * 接口名称
   */
  private String interfaceName;

  /**
   * 协议名称
   */
  private String protocol;

  /**
   * 服务分组
   */
  private String group;

  /**
   * 服务版本
   */
  private String version;

  /**
   * 权重
   */
  private Integer weight;

  /**
   * URL参数
   */
  private Map<String, String> parameters;

  public ServiceURL() {

  }

  public ServiceURL(String host, int port, String interfaceName, String protocol, String group,
      String version, Integer weight, Map<String, String> parameters) {
    this.host = host;
    this.port = port;
    this.interfaceName = interfaceName;
    this.protocol = protocol;
    this.group = group;
    this.version = version;
    this.weight = weight;
    this.parameters = parameters;
  }

  /**
   * 获取服务地址
   */
  public String getAddress() {
    return host + ":" + port;
  }

  /**
   * 转换为InetSocketAddress
   */
  public InetSocketAddress toInetAddress() {
    if (host == null || host.isEmpty()) {
      throw new IllegalStateException("Host is not set in ServiceURL: " + this);
    }
    if (port <= 0) {
      throw new IllegalStateException("Invalid port in ServiceURL: " + this);
    }
    return new InetSocketAddress(host, port);
  }

  /**
   * 获取参数值
   */
  public String getParameter(String key) {
    return parameters == null ? null : parameters.get(key);
  }

  /**
   * 获取参数值，不存在则返回默认值
   */
  public String getParameter(String key, String defaultValue) {
    String value = getParameter(key);
    return value != null ? value : defaultValue;
  }

  /**
   * 添加参数
   */
  public void addParameter(String key, String value) {
    if (parameters == null) {
      parameters = new HashMap<>();
    }
    parameters.put(key, value);
  }

  /**
   * 获取服务标识
   */
  public String getServiceKey() {
    if (serviceKey != null) {
      return serviceKey;
    }
    StringBuilder buf = new StringBuilder();
    if (group != null && !group.isEmpty()) {
      buf.append(group).append("/");
    }
    buf.append(interfaceName);
    if (version != null && !version.isEmpty()) {
      buf.append(":").append(version);
    }
    serviceKey = buf.toString();
    return serviceKey;
  }

  public ServiceMetadata toServiceMetadata() {
    return ServiceMetadata.builder()
        .interfaceName(interfaceName)
        .version(version)
        .group(group)
        .host(host)
        .port(port)
        .protocol(protocol)
        .weight(weight)
        .parameters(parameters)
        .build();
  }

  // protocol://host:port/interfaceName?group=xxx&version=xxx&key1=value1&key2=value2
  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(protocol).append("://")
        .append(host).append(":")
        .append(port).append("/")
        .append(interfaceName);

    if (group != null && !group.isEmpty()) {
      buf.append("?group=").append(group);
    }
    if (version != null && !version.isEmpty()) {
      buf.append(group == null ? "?" : "&");
      buf.append("version=").append(version);
    }

    if (parameters != null && !parameters.isEmpty()) {
      parameters.forEach((key, value) -> {
        if (!"group".equals(key) && !"version".equals(key)) {
          buf.append(group == null && version == null ? "?" : "&");
          buf.append(key).append("=").append(value);
        }
      });
    }

    return buf.toString();
  }
}