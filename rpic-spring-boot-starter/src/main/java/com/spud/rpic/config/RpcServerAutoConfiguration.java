package com.spud.rpic.config;

import com.spud.rpic.config.bean.ServiceStarter;
import com.spud.rpic.io.netty.server.NettyNetServer;
import com.spud.rpic.io.netty.server.RpcServerHandler;
import com.spud.rpic.io.netty.server.RpcServerInitializer;
import com.spud.rpic.io.netty.server.invocation.DefaultServerInvocation;
import com.spud.rpic.io.serializer.Serializer;
import com.spud.rpic.io.serializer.SerializerFactory;
import com.spud.rpic.metrics.RpcMetricsRecorder;
import com.spud.rpic.property.RpcProperties;
import com.spud.rpic.registry.Registry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Spud
 * @date 2025/2/9
 */

@Configuration
@Slf4j
@ConditionalOnProperty(prefix = "rpc", name = "role", havingValue = "server")
@EnableConfigurationProperties(RpcProperties.class)
@AutoConfigureAfter(RpcAutoConfiguration.class)
public class RpcServerAutoConfiguration implements DisposableBean {

	private final ApplicationContext applicationContext;

	public RpcServerAutoConfiguration(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	@Bean
	@ConditionalOnMissingBean
	public DefaultServerInvocation defaultServerInvocation(RpcProperties properties) {
		return new DefaultServerInvocation(properties.getServer());
	}

	@Bean
	@ConditionalOnMissingBean
	public RpcServerHandler rpcServerHandler(Serializer serializer,
		SerializerFactory serializerFactory,
		DefaultServerInvocation defaultServerInvocation,
		RpcMetricsRecorder metricsRecorder) {
		return new RpcServerHandler(serializer, serializerFactory, defaultServerInvocation,
			metricsRecorder);
	}

	@Bean
	@ConditionalOnMissingBean
	public RpcServerInitializer rpcServerInitializer(RpcServerHandler rpcServerHandler) {
		// 启用调试模式
		boolean debugMode = Boolean.getBoolean("rpc.debug");
		return new RpcServerInitializer(rpcServerHandler, debugMode);
	}

	@Bean
	@ConditionalOnMissingBean
	public NettyNetServer rpcServer(RpcProperties properties,
		RpcServerInitializer serverInitializer) {
		return new NettyNetServer(properties, serverInitializer);
	}

	@Bean
	@ConditionalOnMissingBean
	public ServiceStarter serviceStarter(Registry registry, RpcProperties rpcProperties) {
		log.info("Creating ServiceStarter bean for server role");
		return new ServiceStarter(registry, rpcProperties);
	}

	@Override
	public void destroy() throws Exception {
		NettyNetServer nettyNetServer = applicationContext.getBean(NettyNetServer.class);
		try {
			log.info("Shutting down RPC server...");
			nettyNetServer.stop();
			log.info("RPC server stopped successfully");
		} catch (Exception e) {
			log.error("Error stopping RPC server", e);
		}

		// 清理注册中心
	}
}
