package com.spud.rpic.io.netty.server;

import com.spud.rpic.property.RpcProperties;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Slf4j
public class NettyNetServer implements InitializingBean, DisposableBean {

	private final int port;
	private final RpcServerInitializer initializer;
	private final NioEventLoopGroup bossGroup;
	private final NioEventLoopGroup workerGroup;
	private final AtomicBoolean started = new AtomicBoolean(false);
	private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
	private final CountDownLatch startLatch = new CountDownLatch(1);
	private volatile Channel serverChannel;
	private static final Object lock = new Object();

	private void ensureShutdownGuardReset() {
		// helper to reset shuttingDown state if needed (used during tests or restart scenarios)
		shuttingDown.set(false);
	}

	public NettyNetServer(RpcProperties properties, RpcServerInitializer initializer) {
		this.port = properties.getServer().getPort();
		this.initializer = initializer;
		bossGroup = new NioEventLoopGroup(1);
		workerGroup = new NioEventLoopGroup(properties.getClient().getWorkerThreads());
	}

	@Override
	public void afterPropertiesSet() {
		// 在新线程中启动服务器
		new Thread(this::start, "rpc-server-starter").start();
		try {
			// 等待服务器启动完成或超时
			if (!startLatch.await(30, TimeUnit.SECONDS)) {
				throw new RuntimeException("RPC server failed to start within timeout");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("RPC server startup interrupted", e);
		}
	}

	public void start() {
		if (started.get()) {
			return;
		}

		synchronized (lock) {
			if (started.get()) {
				return;
			}
			try {
				ServerBootstrap bootstrap = new ServerBootstrap();
				bootstrap.group(bossGroup, workerGroup)
					.channel(NioServerSocketChannel.class)
					.option(ChannelOption.SO_BACKLOG, 128)
					.option(ChannelOption.SO_REUSEADDR, true)
					.childOption(ChannelOption.SO_KEEPALIVE, true)
					.childOption(ChannelOption.TCP_NODELAY, true)
					.childHandler(initializer);

				// 绑定端口
				ChannelFuture bindFuture = bootstrap.bind(port);
				bindFuture.addListener((ChannelFutureListener) future -> {
					if (future.isSuccess()) {
						serverChannel = future.channel();
						// 添加服务器关闭的监听器
						serverChannel.closeFuture().addListener((ChannelFutureListener) closeFuture -> {
							log.info("RPC server channel closed");
							shutdown();
						});

						started.set(true);
						startLatch.countDown();
						log.info("RPC server started successfully on port {}", port);
					} else {
						log.error("Failed to start RPC server on port {}", port, bindFuture.cause());
						startLatch.countDown();
						shutdown();
					}
				});
			} catch (Exception e) {
				log.error("Error occurred while starting RPC server", e);
				startLatch.countDown();
				shutdown();
				throw new RuntimeException("Failed to start RPC server", e);
			}
		}
	}

	public void shutdown() {
		// prevent re-entrant/recursive shutdown
		if (!started.get() || !shuttingDown.compareAndSet(false, true)) {
			return;
		}

		try {
			// 关闭服务器channel（非阻塞）
			if (serverChannel != null) {
				serverChannel.close().addListener(future -> {
					if (future.isSuccess()) {
						log.info("Server channel closed successfully");
					} else {
						log.warn("Error while closing server channel", future.cause());
					}
				});
			}

			// 优雅关闭线程组
			Future<?> bossShutdownFuture = bossGroup.shutdownGracefully();
			Future<?> workerShutdownFuture = workerGroup.shutdownGracefully();

			try {
				bossShutdownFuture.await(5, TimeUnit.SECONDS);
				workerShutdownFuture.await(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.warn("Interrupted while shutting down RPC server", e);
			}

			started.set(false);
			log.info("RPC server shut down successfully");
		} catch (Exception e) {
			log.error("Error occurred while shutting down RPC server", e);
			throw new RuntimeException("Failed to shutdown RPC server", e);
		} finally {
			// allow future restarts to attempt shutdown again
			shuttingDown.set(false);
		}
	}

	public void stop() throws InterruptedException {
		workerGroup.shutdownGracefully().sync();
		bossGroup.shutdownGracefully().sync();
	}

	@Override
	public void destroy() {
		shutdown();
	}

	public boolean isStarted() {
		return started.get();
	}

	public int getPort() {
		return port;
	}

	public Channel getServerChannel() {
		return serverChannel;
	}
}