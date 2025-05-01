package com.spud.rpic.io.netty.server;

import com.spud.rpic.io.netty.LoggingChannelHandler;
import com.spud.rpic.io.netty.ProtocolDecoder;
import com.spud.rpic.io.netty.ProtocolEncoder;
import com.spud.rpic.io.netty.server.invocation.DefaultServerInvocation;
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
public class RpcServerInitializer extends ChannelInitializer<SocketChannel> {

  // 共享组件
  private final RpcServerHandler sharedHandler;
  private final Serializer serializer;
  private final DefaultServerInvocation defaultServerInvocation;
  // 处理器计数器，用于生成唯一名称
  private final AtomicInteger handlerCounter = new AtomicInteger(0);
  // 是否启用调试模式
  private final boolean debugMode;

  public RpcServerInitializer(RpcServerHandler sharedHandler) {
    this(sharedHandler, false);
  }

  public RpcServerInitializer(RpcServerHandler sharedHandler, boolean debugMode) {
    this.sharedHandler = sharedHandler;
    this.serializer = sharedHandler.getSerializer();
    this.defaultServerInvocation = sharedHandler.getDefaultServerInvocation();
    this.debugMode = debugMode;
  }

  @Override
  protected void initChannel(SocketChannel ch) throws Exception {
    log.debug("Initializing server channel pipeline for channel: {}", ch);

    ChannelPipeline pipeline = ch.pipeline();

    // 添加日志处理器（最前面）
    if (debugMode) {
      pipeline.addLast("logger", new LoggingChannelHandler("SERVER", true));
    }

    // 添加编解码器
    pipeline.addLast("decoder", new ProtocolDecoder());
    pipeline.addLast("encoder", new ProtocolEncoder());

    // 添加日志处理器（解码前后）
    if (debugMode) {
      pipeline.addLast("decodedLogger", new LoggingChannelHandler("SERVER-DECODED", true));
    }

    // 为每个Channel创建独立的RpcServerHandler实例
    String handlerName = "handler-" + handlerCounter.incrementAndGet();
    RpcServerHandler channelHandler = new RpcServerHandler(
        serializer, defaultServerInvocation, sharedHandler);
    pipeline.addLast(handlerName, channelHandler);

    log.debug("Server channel pipeline initialized for channel: {}, handler: {}", ch, handlerName);
  }
}