package com.spud.rpic.property;

import lombok.Data;

/**
 * @author Spud
 * @date 2025/2/27
 */
@Data
public class RpcServerProperties {

  /**
   * 服务端口号
   */
  private int port = 9000;

  /**
   * 最大连接数
   */
  private int maxConnections = 100;

  /**
   * 核心线程数
   */
  private int coreThreads = 20;

  /**
   * 最大线程数
   */
  private int maxThreads = 200;

  /**
   * 线程队列容量
   */
  private int queueSize = 1000;

  /**
   * 心跳间隔(秒)
   */
  private int heartbeatInterval = 30;
}