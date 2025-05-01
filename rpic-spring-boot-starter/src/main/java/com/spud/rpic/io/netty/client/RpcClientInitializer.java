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

  // 共享的RpcClientHandler，只用于获取序列化器和记录回调
  private final RpcClientHandler sharedHandler;
  // 共享的序列化器
  private final Serializer serializer;
  // 处理器计数器，用于生成唯一名称
  private final AtomicInteger handlerCounter = new AtomicInteger(0);
  // 是否启用调试模式
  private final boolean debugMode;

  public RpcClientInitializer(RpcClientHandler sharedHandler, boolean debugMode) {
    this.sharedHandler = sharedHandler;
    this.serializer = sharedHandler.getSerializer();
    this.debugMode = debugMode;
  }

  @Override
  protected void initChannel(SocketChannel ch) throws Exception {
    log.debug("Initializing channel pipeline for channel: {}", ch);
    ChannelPipeline pipeline = ch.pipeline();

    // 添加日志处理器（最前面）
    if (debugMode) {
      pipeline.addLast("logger", new LoggingChannelHandler("CLIENT", true));
    }

    // 移除编码器，我们手动编码ByteBuf
    // 只保留解码器（入站数据）
    pipeline.addLast("decoder", new ProtocolDecoder());

    // 添加日志处理器（解码后）
    if (debugMode) {
      pipeline.addLast("decodedLogger", new LoggingChannelHandler("CLIENT-DECODED", true));
    }

    // 为每个Channel创建独立的RpcClientHandler实例
    // 但它们共享同一个序列化器
    String handlerName = "handler-" + handlerCounter.incrementAndGet();
    RpcClientHandler channelHandler = new RpcClientHandler(serializer, sharedHandler);
    pipeline.addLast(handlerName, channelHandler);

    log.debug("Channel pipeline initialized for channel: {}, handler: {}", ch, handlerName);
  }
}