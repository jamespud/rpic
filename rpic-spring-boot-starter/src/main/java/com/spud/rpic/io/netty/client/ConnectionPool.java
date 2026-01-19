package com.spud.rpic.io.netty.client;

import com.spud.rpic.common.exception.RpcException;
import com.spud.rpic.metrics.RpcMetricsRecorder;
import com.spud.rpic.model.ServiceURL;
import com.spud.rpic.property.RpcClientProperties;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelHealthChecker;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Spud
 * @date 2025/2/13
 */

@Slf4j
public class ConnectionPool {

	private final AbstractChannelPoolMap<InetSocketAddress, FixedChannelPool> poolMap;

	private final EventLoopGroup group;

	private final Bootstrap bootstrap;

	private final int acquireTimeout;

	private final RpcClientProperties.ConnectionPoolProperties poolProperties;

	private final boolean epollEnabled;

	private final Map<InetSocketAddress, AtomicInteger> connectionCounter = new ConcurrentHashMap<>();

	private final RpcClientHandler sharedHandler;

	private final boolean debugMode;

	private final RpcMetricsRecorder metricsRecorder;

	public ConnectionPool(RpcClientProperties clientProperties, RpcClientInitializer initializer,
		RpcMetricsRecorder metricsRecorder) {
		this.poolProperties = clientProperties.getConnectionPoolProperties();
		this.acquireTimeout = poolProperties.getAcquireTimeout();
		int maxConnectionsPerAddress = poolProperties.getMaxConnectionsPerAddress() > 0
			? poolProperties.getMaxConnectionsPerAddress()
			: poolProperties.getMaxConnections();
		int maxPendingAcquires = poolProperties.getMaxPendingAcquires();
		this.sharedHandler = initializer.getSharedHandler();
		this.debugMode = Boolean.getBoolean("rpc.debug");
		this.metricsRecorder = metricsRecorder;

		this.epollEnabled = clientProperties.isUseEpoll() && Epoll.isAvailable();

		// 使用更多的工作线程
		int workerThreads = clientProperties.getWorkerThreads();
		group = epollEnabled
			? new EpollEventLoopGroup(workerThreads <= 0 ? 0 : workerThreads)
			: new NioEventLoopGroup(workerThreads <= 0 ? 0 : workerThreads);

		log.info("ConnectionPool using {} transport with workerThreads={} (<=0 means Netty default)",
			epollEnabled ? "epoll" : "nio",
			workerThreads);

		bootstrap = new Bootstrap()
			.group(group)
			.channel(epollEnabled ? EpollSocketChannel.class : NioSocketChannel.class)
			.option(ChannelOption.SO_REUSEADDR, clientProperties.isSoReuseAddr())
			.option(ChannelOption.SO_KEEPALIVE, clientProperties.isSoKeepalive())
			.option(ChannelOption.TCP_NODELAY, clientProperties.isTcpNoDelay())
			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, clientProperties.getConnectTimeout())
			.option(ChannelOption.ALLOCATOR,
				clientProperties.isPooledAllocator() ? PooledByteBufAllocator.DEFAULT
					: io.netty.buffer.UnpooledByteBufAllocator.DEFAULT)
			.option(ChannelOption.WRITE_BUFFER_WATER_MARK,
				new WriteBufferWaterMark(clientProperties.getWriteBufferWaterMarkLow(),
					clientProperties.getWriteBufferWaterMarkHigh()))
			.option(ChannelOption.RCVBUF_ALLOCATOR, new AdaptiveRecvByteBufAllocator());

		if (clientProperties.getSndBuf() != null) {
			bootstrap.option(ChannelOption.SO_SNDBUF, clientProperties.getSndBuf());
		}
		if (clientProperties.getRcvBuf() != null) {
			bootstrap.option(ChannelOption.SO_RCVBUF, clientProperties.getRcvBuf());
		}

		poolMap = new AbstractChannelPoolMap<InetSocketAddress, FixedChannelPool>() {
			@Override
			protected FixedChannelPool newPool(InetSocketAddress address) {
				// 创建新的Bootstrap以确保每个连接池都有自己的地址配置
				Bootstrap newBootstrap = bootstrap.clone();
				// 在这里显式设置远程地址
				newBootstrap.remoteAddress(address);

				log.debug("Creating new channel pool for address: {}", address);

				AtomicInteger counter = connectionCounter.computeIfAbsent(address,
					key -> new AtomicInteger());
				registerGaugeIfNecessary(address, counter);

				return new FixedChannelPool(
					newBootstrap,
					new RpcChannelPoolHandler(sharedHandler, debugMode),
					ChannelHealthChecker.ACTIVE,
					FixedChannelPool.AcquireTimeoutAction.FAIL,
					acquireTimeout,
					maxConnectionsPerAddress,
					maxPendingAcquires,
					true,
					true);
			}
		};
	}

	private SimpleChannelPool getPool(InetSocketAddress address) {
		log.debug("Getting channel pool for address: {}", address);
		return poolMap.get(address);
	}

	public Channel acquireChannel(ServiceURL serviceUrl) throws Exception {
		if (serviceUrl == null) {
			throw new RpcException("ServiceURL cannot be null");
		}

		if (serviceUrl.getHost() == null || serviceUrl.getHost().isEmpty()) {
			throw new RpcException("Service host cannot be null or empty: " + serviceUrl);
		}

		InetSocketAddress address = serviceUrl.toInetAddress();
		log.debug("Acquiring channel for address: {}", address);

		SimpleChannelPool pool = getPool(address);
		if (pool == null) {
			recordPoolAcquireFailure(address);
			throw new RpcException("Failed to get channel pool for address: " + address);
		}

		try {
			Channel channel = pool.acquire().get(acquireTimeout, TimeUnit.MILLISECONDS);


			if (!channel.isActive()) {
				pool.release(channel);
				recordPoolAcquireFailure(address);
				throw new RpcException("Channel is not active");
			}

			// 记录连接数（仅在确认 channel 活跃后）
			AtomicInteger counter = connectionCounter.computeIfAbsent(address, k -> new AtomicInteger(0));
			counter.incrementAndGet();

			recordPoolAcquireSuccess(address);
			log.debug("Successfully acquired channel for address: {}, channel: {}", address, channel);
			return channel;
		} catch (Exception e) {
			recordPoolAcquireFailure(address);
			log.error("Failed to acquire channel for address: {}, error: {}", address, e.getMessage());
			throw new RpcException("Failed to acquire channel for address: " + address, e);
		}
	}

	public CompletableFuture<Channel> acquireChannelAsync(ServiceURL serviceUrl) {
		CompletableFuture<Channel> future = new CompletableFuture<>();

		if (serviceUrl == null) {
			future.completeExceptionally(new RpcException("ServiceURL cannot be null"));
			return future;
		}

		if (serviceUrl.getHost() == null || serviceUrl.getHost().isEmpty()) {
			future.completeExceptionally(
				new RpcException("Service host cannot be null or empty: " + serviceUrl));
			return future;
		}

		InetSocketAddress address = serviceUrl.toInetAddress();
		log.debug("Acquiring channel async for address: {}", address);

		SimpleChannelPool pool = poolMap.get(address);
		if (pool == null) {
			recordPoolAcquireFailure(address);
			future.completeExceptionally(
				new RpcException("Failed to get channel pool for address: " + address));
			return future;
		}

		pool.acquire().addListener(channelFuture -> {
			if (channelFuture.isSuccess()) {
				Channel channel = (Channel) channelFuture.getNow();
				if (channel.isActive()) {
					AtomicInteger counter = connectionCounter.computeIfAbsent(address,
						k -> new AtomicInteger(0));
					counter.incrementAndGet();
					log.debug("Successfully acquired channel async for address: {}, channel: {}", address,
						channel);
					recordPoolAcquireSuccess(address);
					future.complete(channel);
				} else {
					pool.release(channel);
					recordPoolAcquireFailure(address);
					future.completeExceptionally(new RpcException("Channel is not active"));
				}
			} else {
				log.error("Failed to acquire channel async for address: {}, error: {}",
					address, channelFuture.cause().getMessage());
				recordPoolAcquireFailure(address);
				future.completeExceptionally(channelFuture.cause());
			}
		});

		return future;
	}

	public void releaseChannel(ServiceURL serviceUrl, Channel channel) {
		if (channel == null) {
			return;
		}

		InetSocketAddress address = serviceUrl.toInetAddress();
		releaseChannel(address, channel);
	}

	public void releaseChannel(Channel channel) {
		if (channel == null) {
			return;
		}
		InetSocketAddress address = (InetSocketAddress) channel.remoteAddress();
		releaseChannel(address, channel);
	}

	public void releaseChannel(InetSocketAddress address, Channel channel) {
		if (channel == null) {
			return;
		}
		SimpleChannelPool pool = poolMap.get(address);
		if (pool != null) {
			pool.release(channel);
			log.debug("Released channel for address: {}, channel: {}", address, channel);

			// 减少连接计数
			AtomicInteger counter = connectionCounter.get(address);
			if (counter != null) {
				counter.decrementAndGet();
			}
		}
	}

	public void close() {
		poolMap.forEach(entry -> {
			entry.getValue().close();
		});
		group.shutdownGracefully();
		log.info("Connection pool closed");
	}

	public Map<String, Integer> getConnectionStats() {
		Map<String, Integer> stats = new HashMap<>();
		connectionCounter
			.forEach((address, counter) -> stats.put(address.getHostString() + ":" + address.getPort(),
				counter.get()));
		return stats;
	}

	private void registerGaugeIfNecessary(InetSocketAddress address, AtomicInteger counter) {
		if (metricsRecorder == null || !metricsRecorder.isEnabled()) {
			return;
		}
		String endpoint = endpointTag(address);
		metricsRecorder.registerActiveConnectionsGauge(endpoint, counter::get);
	}

	private void recordPoolAcquireSuccess(InetSocketAddress address) {
		if (metricsRecorder == null || !metricsRecorder.isEnabled()) {
			return;
		}
		metricsRecorder.recordPoolAcquire(endpointTag(address), true);
	}

	private void recordPoolAcquireFailure(InetSocketAddress address) {
		if (metricsRecorder == null || !metricsRecorder.isEnabled()) {
			return;
		}
		metricsRecorder.recordPoolAcquire(endpointTag(address), false);
	}

	private String endpointTag(InetSocketAddress address) {
		return address.getHostString() + ":" + address.getPort();
	}
}