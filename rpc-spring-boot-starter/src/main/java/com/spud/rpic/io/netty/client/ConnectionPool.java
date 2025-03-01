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

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Spud
 * @date 2025/2/13
 */
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
        group = new NioEventLoopGroup();
        bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(initializer);
        poolMap = new AbstractChannelPoolMap<InetSocketAddress, FixedChannelPool>() {
            @Override
            protected FixedChannelPool newPool(InetSocketAddress key) {
                return new FixedChannelPool(
                        bootstrap,
                        new RpcChannelPoolHandler(),
                        ChannelHealthChecker.ACTIVE,
                        FixedChannelPool.AcquireTimeoutAction.FAIL,
                        acquireTimeout,
                        maxConnections,
                        maxConnections / 3,
                        true,
                        true
                );
            }
        };
    }

    private SimpleChannelPool getPool(InetSocketAddress address) {
        return poolMap.get(address);
    }

    public Channel acquireChannel(ServiceURL serviceUrl) throws Exception {
        InetSocketAddress address = serviceUrl.toInetAddress();
        SimpleChannelPool pool = getPool(address);
        Channel channel = pool.acquire().get(acquireTimeout, TimeUnit.MILLISECONDS);

        // 记录连接数
        AtomicInteger counter = connectionCounter.computeIfAbsent(address, k -> new AtomicInteger(0));
        counter.incrementAndGet();

        if (!channel.isActive()) {
            pool.release(channel);
            throw new RpcException("Channel is not active");
        }
        return channel;
    }

    public CompletableFuture<Channel> acquireChannelAsync(ServiceURL serviceUrl) {
        CompletableFuture<Channel> future = new CompletableFuture<>();
        SimpleChannelPool pool = poolMap.get(serviceUrl.toInetAddress());

        pool.acquire().addListener(channelFuture -> {
            if (channelFuture.isSuccess()) {
                Channel channel = (Channel) channelFuture.getNow();
                if (channel.isActive()) {
                    future.complete(channel);
                } else {
                    pool.release(channel);
                    future.completeExceptionally(new RpcException("Channel is not active"));
                }
            } else {
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
    }

    public Map<String, Integer> getConnectionStats() {
        Map<String, Integer> stats = new HashMap<>();
        connectionCounter.forEach((address, counter) ->
                stats.put(address.getHostString() + ":" + address.getPort(), counter.get()));
        return stats;
    }
}