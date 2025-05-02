package com.spud.rpic.io.netty.client;

import com.spud.rpic.io.netty.LoggingChannelHandler;
import com.spud.rpic.io.netty.ProtocolDecoder;
import com.spud.rpic.io.netty.ProtocolEncoder;
import com.spud.rpic.io.serializer.Serializer;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Slf4j
public class RpcClientInitializer extends ChannelInitializer<SocketChannel> {

  private final RpcClientHandler sharedHandler;
  private final boolean debugMode;
  private final AtomicInteger handlerCounter = new AtomicInteger(0);

  public RpcClientInitializer(RpcClientHandler sharedHandler, boolean debugMode) {
    this.sharedHandler = sharedHandler;
    this.debugMode = debugMode;
  }

  @Override
  protected void initChannel(SocketChannel ch) {
    ChannelPipeline pipeline = ch.pipeline();

    // 添加调试日志处理器（如果启用）
    if (debugMode) {
      pipeline.addLast("logger", new LoggingChannelHandler("CLIENT", true));
    }

    // 添加协议编解码器
    pipeline.addLast("decoder", new ProtocolDecoder());
    pipeline.addLast("encoder", new ProtocolEncoder());

    // 为每个channel创建独立的handler实例，共享状态
    String handlerName = "handler-" + handlerCounter.incrementAndGet();
    RpcClientHandler channelHandler = new RpcClientHandler(sharedHandler.getSerializer(), sharedHandler);
    pipeline.addLast(handlerName, channelHandler);

    log.debug("Initialized client channel pipeline for {}, handler: {}", ch, handlerName);
  }

  /**
   * 获取共享的RpcClientHandler实例
   */
  public RpcClientHandler getSharedHandler() {
    return sharedHandler;
  }
}