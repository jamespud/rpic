package com.spud.rpic.io.netty.client;

import io.netty.channel.Channel;
import io.netty.channel.pool.AbstractChannelPoolHandler;
import io.netty.util.AttributeKey;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Spud
 * @date 2025/2/17
 */
@Slf4j
public class RpcChannelPoolHandler extends AbstractChannelPoolHandler {

  private static final AttributeKey<Long> LAST_ACCESS_TIME = AttributeKey.valueOf("lastAccessTime");

  private static final int MAX_IDLE_MINUTES = 15;

  @Override
  public void channelCreated(Channel ch) {
    // 获取远程地址信息
    InetSocketAddress remoteAddress = null;
    try {
      remoteAddress = (InetSocketAddress) ch.remoteAddress();
    } catch (Exception e) {
      // Channel可能尚未连接，无法获取远程地址
      log.debug("Channel not connected yet, cannot get remote address: {}", ch);
    }

    log.debug("Channel created: {}, remote address: {}", ch, remoteAddress);
    ch.attr(LAST_ACCESS_TIME).set(System.nanoTime());
  }

  @Override
  public void channelReleased(Channel ch) throws Exception {
    if (!ch.isActive()) {
      log.debug("Channel released but not active, closing: {}", ch);
      ch.close();
      return;
    }

    ch.attr(LAST_ACCESS_TIME).set(System.nanoTime());
    log.debug("Channel released and last access time updated: {}", ch);
    super.channelReleased(ch);
  }

  @Override
  public void channelAcquired(Channel ch) {
    long idleNanos = System.nanoTime() - ch.attr(LAST_ACCESS_TIME).get();
    long idleMinutes = TimeUnit.NANOSECONDS.toMinutes(idleNanos);
    log.debug("Channel acquired: {}, idle time: {} minutes", ch, idleMinutes);

    if (idleMinutes > MAX_IDLE_MINUTES && !ch.isActive()) {
      log.debug("Channel idle for too long and inactive, closing: {}", ch);
      ch.close();
    } else {
      ch.attr(LAST_ACCESS_TIME).set(System.nanoTime());
    }
  }
}
