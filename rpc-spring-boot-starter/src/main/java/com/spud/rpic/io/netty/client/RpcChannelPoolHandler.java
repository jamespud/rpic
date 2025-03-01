package com.spud.rpic.io.netty.client;

import io.netty.channel.Channel;
import io.netty.channel.pool.AbstractChannelPoolHandler;
import io.netty.util.AttributeKey;

import java.util.concurrent.TimeUnit;

/**
 * @author Spud
 * @date 2025/2/17
 */
public class RpcChannelPoolHandler extends AbstractChannelPoolHandler{

    private static final AttributeKey<Long> LAST_ACCESS_TIME =
            AttributeKey.valueOf("lastAccessTime");

    private static final int MAX_IDLE_MINUTES = 5;

    @Override
    public void channelCreated(Channel ch) {
        ch.attr(LAST_ACCESS_TIME).set(System.nanoTime());
    }

    @Override
    public void channelReleased(Channel ch) throws Exception {
        if (!ch.isActive()) {
            ch.close();
            return;
        }
        super.channelReleased(ch);
        ch.attr(LAST_ACCESS_TIME).set(System.nanoTime());
    }

    @Override
    public void channelAcquired(Channel ch) {
        long idleNanos = System.nanoTime() - ch.attr(LAST_ACCESS_TIME).get();
        if (idleNanos > TimeUnit.MINUTES.toNanos(MAX_IDLE_MINUTES)) {
            ch.close(); // 触发池重建连接
        }
    }
    
}
