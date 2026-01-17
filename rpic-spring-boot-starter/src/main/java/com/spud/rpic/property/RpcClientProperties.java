package com.spud.rpic.property;

import java.util.Arrays;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
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
	@NotBlank(message = "Loadbalance type cannot be blank")
	private String loadbalance = "random";

	/**
	 * 调用超时时间(毫秒)
	 */
	@Positive(message = "Timeout must be positive")
	private int timeout = 5000;

	/**
	 * 重试参数
	 */
	@Valid
	private RetryProperties retry = new RetryProperties();

	/**
	 * 连接超时时间(毫秒)
	 */
	@Positive(message = "Connect timeout must be positive")
	private int connectTimeout = 3000;

	/**
	 * 心跳发送间隔(秒)
	 */
	@Positive(message = "Heartbeat interval must be positive")
	private int heartbeatInterval = 30;

	@PositiveOrZero(message = "Worker threads must be positive or zero")
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

	@Positive(message = "Write buffer water mark low must be positive")
	private int writeBufferWaterMarkLow = 32 * 1024;

	@Positive(message = "Write buffer water mark high must be positive")
	private int writeBufferWaterMarkHigh = 64 * 1024;

	@Positive(message = "Idle ping seconds must be positive")
	private int idlePingSeconds = 30;

	@Valid
	private ConnectionPoolProperties connectionPoolProperties = new ConnectionPoolProperties();

	/**
	 * 熔断配置
	 */
	@Valid
	private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();

	/**
	 * 异常节点剔除配置
	 */
	@Valid
	private OutlierEjectionProperties outlier = new OutlierEjectionProperties();

	@Data
	public static class ConnectionPoolProperties {

		/**
		 * 连接池最大空闲时间(秒)
		 */
		@Positive(message = "Max idle time must be positive")
		private int maxIdleTime = 60;

		/**
		 * 连接池最大连接数
		 */
		@Positive(message = "Max connections must be positive")
		private int maxConnections = 100;

		/**
		 * 获取连接超时时间(毫秒)
		 */
		@Positive(message = "Acquire timeout must be positive")
		private int acquireTimeout = 5000;
		/**
		 * 连接池最大请求数
		 */
		@PositiveOrZero(message = "Max pending acquires must be positive or zero")
		private int maxPendingAcquires = 10_000;

		/**
		 * 每个地址的最大连接数
		 */
		@Positive(message = "Max connections per address must be positive")
		private int maxConnectionsPerAddress = 50;

		/**
		 * 连接池健康检查间隔(秒)
		 */
		@Positive(message = "Health check interval must be positive")
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
		@Min(1)
		private int maxAttempts = 3;

		/**
		 * 初始回退时间（毫秒）
		 */
		@PositiveOrZero(message = "Base delay must be positive or zero")
		private long baseDelayMs = 50L;

		/**
		 * 回退最大时间（毫秒）
		 */
		@PositiveOrZero(message = "Max delay must be positive or zero")
		private long maxDelayMs = 1000L;

		/**
		 * 指数回退倍率
		 */
		@Positive(message = "Multiplier must be positive")
		private double multiplier = 2.0d;

		/**
		 * 抖动因子（0-1）
		 */
		@Min(0)
		@Max(1)
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
		@Min(0)
		@Max(100)
		private float failureRateThreshold = 50.0f;
		@Min(0)
		@Max(100)
		private float slowCallRateThreshold = 50.0f;
		@Positive(message = "Slow call duration threshold must be positive")
		private long slowCallDurationThresholdMs = 1000L;
		@Positive(message = "Sliding window size must be positive")
		private int slidingWindowSize = 100;
		@Positive(message = "Minimum number of calls must be positive")
		private int minimumNumberOfCalls = 50;
		@Positive(message = "Wait duration in open state must be positive")
		private long waitDurationInOpenStateMs = 10_000L;
		private boolean perEndpoint = true;
	}

	@Data
	public static class OutlierEjectionProperties {

		private boolean enabled = true;
		@Min(0)
		@Max(1)
		private double errorRateThreshold = 0.5d;
		@Positive(message = "Min request volume must be positive")
		private int minRequestVolume = 50;
		@Positive(message = "Ejection duration must be positive")
		private long ejectionDurationMs = 30_000L;
		@Positive(message = "Probe interval must be positive")
		private long probeIntervalMs = 5_000L;
	}
}
