package com.spud.rpic.property;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
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
	@Min(1024)
	@Max(65535)
	private int port = 9000;

	/**
	 * 最大连接数
	 */
	@Positive(message = "Max connections must be positive")
	private int maxConnections = 100;

	/**
	 * 核心线程数
	 */
	@PositiveOrZero(message = "Core threads must be positive or zero")
	private int coreThreads = 20;

	/**
	 * 最大线程数
	 */
	@Positive(message = "Max threads must be positive")
	private int maxThreads = 200;

	/**
	 * 线程队列容量
	 */
	@PositiveOrZero(message = "Queue size must be positive or zero")
	private int queueSize = 1000;

	/**
	 * 心跳间隔(秒)
	 */
	@Positive(message = "Heartbeat interval must be positive")
	private int heartbeatInterval = 30;

	/**
	 * 最大并发请求数
	 */
	@Positive(message = "Max concurrent requests must be positive")
	private int maxConcurrentRequests = 100;

	@PositiveOrZero(message = "Boss threads must be positive or zero")
	private int bossThreads = 1;

	@PositiveOrZero(message = "Worker threads must be positive or zero")
	private int workerThreads = 0;

	private boolean useEpoll = true;

	private boolean pooledAllocator = true;

	private boolean tcpNoDelay = true;

	private boolean soKeepalive = true;

	private boolean soReuseAddr = true;

	@PositiveOrZero(message = "So backlog must be positive or zero")
	private int soBacklog = 512;

	private Integer sndBuf;

	private Integer rcvBuf;

	@Positive(message = "Write buffer water mark low must be positive")
	private int writeBufferWaterMarkLow = 32 * 1024;

	@Positive(message = "Write buffer water mark high must be positive")
	private int writeBufferWaterMarkHigh = 64 * 1024;
}