package com.spud.rpic.io.netty.client;

import com.spud.rpic.common.exception.RpcException;
import com.spud.rpic.model.ServiceURL;
import com.spud.rpic.property.RpcClientProperties;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelHealthChecker;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.Future;
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

  private final Map<InetSocketAddress, AtomicInteger> connectionCounter = new ConcurrentHashMap<>();

  public ConnectionPool(RpcClientProperties clientProperties, RpcClientInitializer initializer) {
    RpcClientProperties.ConnectionPoolProperties poolProperties = clientProperties.getConnectionPoolProperties();
    acquireTimeout = poolProperties.getAcquireTimeout();
    int maxConnections = poolProperties.getMaxConnections();

    // 使用更多的工作线程
    group = new NioEventLoopGroup(clientProperties.getWorkerThreads());

    bootstrap = new Bootstrap()
        .group(group)
        .channel(NioSocketChannel.class)
        .option(ChannelOption.SO_REUSEADDR, true)
        .option(ChannelOption.SO_KEEPALIVE, true)
        .option(ChannelOption.TCP_NODELAY, true)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, clientProperties.getConnectTimeout())
        // 增加写缓冲区大小，提高吞吐量
        .option(ChannelOption.SO_SNDBUF, 32 * 1024)
        .option(ChannelOption.SO_RCVBUF, 32 * 1024)
        // 允许地址重用
        .option(ChannelOption.SO_REUSEADDR, true)
        .handler(initializer);

    poolMap = new AbstractChannelPoolMap<InetSocketAddress, FixedChannelPool>() {
      @Override
      protected FixedChannelPool newPool(InetSocketAddress address) {
        // 创建新的Bootstrap以确保每个连接池都有自己的地址配置
        Bootstrap newBootstrap = bootstrap.clone();
        // 在这里显式设置远程地址
        newBootstrap.remoteAddress(address);

        log.debug("Creating new channel pool for address: {}", address);

        return new FixedChannelPool(
            newBootstrap,
            new RpcChannelPoolHandler(),
            ChannelHealthChecker.ACTIVE,
            FixedChannelPool.AcquireTimeoutAction.FAIL,
            acquireTimeout,
            maxConnections,
            // 增加最大等待获取连接的数量
            maxConnections,
            // 启用健康检查
            true,
            // 启用释放健康检查
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
      throw new RpcException("Failed to get channel pool for address: " + address);
    }

    try {
      Channel channel = pool.acquire().get(acquireTimeout, TimeUnit.MILLISECONDS);

      // 记录连接数
      AtomicInteger counter = connectionCounter.computeIfAbsent(address, k -> new AtomicInteger(0));
      counter.incrementAndGet();

      if (!channel.isActive()) {
        pool.release(channel);
        throw new RpcException("Channel is not active");
      }

      log.debug("Successfully acquired channel for address: {}, channel: {}", address, channel);
      return channel;
    } catch (Exception e) {
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
      future.completeExceptionally(new RpcException("Service host cannot be null or empty: " + serviceUrl));
      return future;
    }

    InetSocketAddress address = serviceUrl.toInetAddress();
    log.debug("Acquiring channel async for address: {}", address);

    SimpleChannelPool pool = poolMap.get(address);
    if (pool == null) {
      future.completeExceptionally(new RpcException("Failed to get channel pool for address: " + address));
      return future;
    }

    pool.acquire().addListener(channelFuture -> {
      if (channelFuture.isSuccess()) {
        Channel channel = (Channel) channelFuture.getNow();
        if (channel.isActive()) {
          log.debug("Successfully acquired channel async for address: {}, channel: {}", address, channel);
          future.complete(channel);
        } else {
          pool.release(channel);
          future.completeExceptionally(new RpcException("Channel is not active"));
        }
      } else {
        log.error("Failed to acquire channel async for address: {}, error: {}",
            address, channelFuture.cause().getMessage());
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
        .forEach((address, counter) -> stats.put(address.getHostString() + ":" + address.getPort(), counter.get()));
    return stats;
  }
}