package com.spud.rpic.property;

import java.util.Arrays;
import java.util.List;
import lombok.Data;

/**
 * @author Spud
 * @date 2025/2/27
 */
@Data
public class RpcClientProperties {

	/**
	 * 负载均衡类型（random、round_robin、weighted_random、weighted_round_robin、p2c_ewma）
	 */
	private String loadbalance = "random";

	/**
	 * 调用超时时间(毫秒)
	 */
	private int timeout = 5000;

	/**
	 * 重试参数
	 */
	private RetryProperties retry = new RetryProperties();

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

	/**
	 * 熔断配置
	 */
	private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();

	/**
	 * 异常节点剔除配置
	 */
	private OutlierEjectionProperties outlier = new OutlierEjectionProperties();

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

	@Data
	public static class RetryProperties {

		/**
		 * 是否启用重试
		 */
		private boolean enabled = true;

		/**
		 * 最大尝试次数（含首次调用）
		 */
		private int maxAttempts = 3;

		/**
		 * 初始回退时间（毫秒）
		 */
		private long baseDelayMs = 50L;

		/**
		 * 回退最大时间（毫秒）
		 */
		private long maxDelayMs = 1000L;

		/**
		 * 指数回退倍率
		 */
		private double multiplier = 2.0d;

		/**
		 * 抖动因子（0-1）
		 */
		private double jitterFactor = 1.0d;

		/**
		 * 按异常类型判定是否允许重试
		 */
		private List<String> retryOnExceptions = Arrays.asList(
			"com.spud.rpic.common.exception.TimeoutException",
			"com.spud.rpic.common.exception.RemoteException",
			"com.spud.rpic.common.exception.RpcException"
		);

		/**
		 * 若错误信息包含以下关键字则允许重试
		 */
		private List<String> retryOnErrorMsgContains = Arrays.asList("UNAVAILABLE", "TIMEOUT",
			"DEADLINE_EXCEEDED");
	}

	@Data
	public static class CircuitBreakerProperties {

		private boolean enabled = true;
		private float failureRateThreshold = 50.0f;
		private float slowCallRateThreshold = 50.0f;
		private long slowCallDurationThresholdMs = 1000L;
		private int slidingWindowSize = 100;
		private int minimumNumberOfCalls = 50;
		private long waitDurationInOpenStateMs = 10_000L;
		private boolean perEndpoint = true;
	}

	@Data
	public static class OutlierEjectionProperties {

		private boolean enabled = true;
		private double errorRateThreshold = 0.5d;
		private int minRequestVolume = 50;
		private long ejectionDurationMs = 30_000L;
		private long probeIntervalMs = 5_000L;
	}
}
