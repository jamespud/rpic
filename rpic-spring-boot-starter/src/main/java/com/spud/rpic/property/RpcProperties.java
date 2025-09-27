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
	private String serializeType = "kryo";

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

	@NestedConfigurationProperty
	private MetricsProperties metrics = new MetricsProperties();

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

	@Data
	public static class MetricsProperties {

		/**
		 * 指标是否启用
		 */
		private boolean enabled = true;

		/**
		 * 百分位统计
		 */
		private double[] percentiles = new double[]{0.5, 0.9, 0.95, 0.99};

		/**
		 * SLA 边界 (毫秒)
		 */
		private long[] slaMs = new long[]{1, 5, 10, 20, 50, 100, 200, 500};

		/**
		 * 是否启用直方图
		 */
		private boolean histogram = true;

		/**
		 * 是否开启高基数标签
		 */
		private boolean highCardinalityTagsEnabled = false;
	}
}