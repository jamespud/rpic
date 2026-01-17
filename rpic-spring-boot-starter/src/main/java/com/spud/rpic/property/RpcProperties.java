package com.spud.rpic.property;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Data
@Validated
@ConfigurationProperties(prefix = "rpc")
public class RpcProperties {

	@NotBlank(message = "Role cannot be blank")
	private String role = "client";

	/**
	 * 应用名称
	 */
	@NotBlank(message = "Application name cannot be blank")
	@Size(min = 1, max = 100, message = "Application name must be between 1 and 100 characters")
	private String applicationName = "rpc-application";

	/**
	 * 序列化类型
	 */
	@NotBlank(message = "Serialize type cannot be blank")
	private String serializeType = "kryo";

	/**
	 * 压缩类型
	 */
	@NotBlank(message = "Compress type cannot be blank")
	private String compressType = "none";

	/**
	 * 注册中心配置
	 */
	@Valid
	private RegistryConfig registry = new RegistryConfig();

	@Valid
	@NestedConfigurationProperty
	private RpcServerProperties server = new RpcServerProperties();

	@Valid
	@NestedConfigurationProperty
	private RpcClientProperties client = new RpcClientProperties();

	@Valid
	@NestedConfigurationProperty
	private MetricsProperties metrics = new MetricsProperties();

	@Data
	public static class RegistryConfig {

		/**
		 * 注册中心类型(nacos, zookeeper)
		 */
		@NotBlank(message = "Registry type cannot be blank")
		private String type = "nacos";

		/**
		 * 注册中心地址
		 */
		@NotBlank(message = "Registry address cannot be blank")
		private String address = "localhost:8848";

		/**
		 * 注册超时时间
		 */
		@Positive(message = "Registry timeout must be positive")
		private int timeout = 3000;

		// 注册缓存配置
		@Positive(message = "Registration TTL must be positive")
		private long registrationTtl = 30;  // 分钟
		@Positive(message = "Registration refresh must be positive")
		private long registrationRefresh = 15;  // 分钟

		// 发现缓存配置
		@Positive(message = "Discovery TTL must be positive")
		private long discoveryTtl = 30;  // 秒
		@Positive(message = "Discovery refresh must be positive")
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