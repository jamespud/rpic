package com.spud.rpic.property;

import lombok.Data;

/**
 * @author Spud
 * @date 2025/2/27
 */
@Data
public class RpcClientProperties {

	/**
	 * 负载均衡类型
	 */
	private String loadbalance = "random";

	/**
	 * 调用超时时间(毫秒)
	 */
	private int timeout = 5000;

	/**
	 * 重试次数
	 */
	private int retries = 2;

	/**
	 * 连接超时时间(毫秒)
	 */
	private int connectTimeout = 3000;

	/**
	 * 心跳发送间隔(秒)
	 */
	private int heartbeatInterval = 30;

	private int workerThreads = 0;

	/**
	 * 是否启用Epoll
	 */
	private boolean useEpoll = true;

	/**
	 * 是否使用池化分配器
	 */
	private boolean pooledAllocator = true;

	private boolean tcpNoDelay = true;

	private boolean soKeepalive = true;

	private boolean soReuseAddr = true;

	private Integer sndBuf;

	private Integer rcvBuf;

	private int writeBufferWaterMarkLow = 32 * 1024;

	private int writeBufferWaterMarkHigh = 64 * 1024;

	private int idlePingSeconds = 30;

	private ConnectionPoolProperties connectionPoolProperties = new ConnectionPoolProperties();

	@Data
	public static class ConnectionPoolProperties {

		/**
		 * 连接池最大空闲时间(秒)
		 */
		private int maxIdleTime = 60;

		/**
		 * 连接池最大连接数
		 */
		private int maxConnections = 100;

		/**
		 * 获取连接超时时间(毫秒)
		 */
		private int acquireTimeout = 5000;
		/**
		 * 连接池最大请求数
		 */
		private int maxPendingAcquires = 10_000;

		/**
		 * 每个地址的最大连接数
		 */
		private int maxConnectionsPerAddress = 50;

		/**
		 * 连接池健康检查间隔(秒)
		 */
		private int healthCheckInterval = 60;

	}
}
