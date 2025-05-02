package com.spud.rpic.io.netty.server;

import com.spud.rpic.common.constants.RpcConstants;
import com.spud.rpic.common.domain.RpcRequest;
import com.spud.rpic.common.domain.RpcResponse;
import com.spud.rpic.io.common.ProtocolMsg;
import com.spud.rpic.io.netty.server.invocation.DefaultServerInvocation;
import com.spud.rpic.io.serializer.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Slf4j
public class RpcServerHandler extends SimpleChannelInboundHandler<ProtocolMsg> {

  private final Serializer serializer;
  private final DefaultServerInvocation defaultServerInvocation;
  private volatile ChannelHandlerContext context;
  private final RpcServerHandler masterHandler;

  /**
   * 创建主Handler实例（由Spring管理的单例）
   */
  public RpcServerHandler(Serializer serializer, DefaultServerInvocation defaultServerInvocation) {
    this.serializer = serializer;
    this.defaultServerInvocation = defaultServerInvocation;
    this.masterHandler = null; // 自身就是主handler
    log.debug("Created master RpcServerHandler with serializer: {}", serializer.getType());
  }

  /**
   * 为每个Channel创建独立的Handler实例，但共享序列化器和调用器
   */
  public RpcServerHandler(Serializer serializer, DefaultServerInvocation defaultServerInvocation,
      RpcServerHandler masterHandler) {
    this.serializer = serializer;
    this.defaultServerInvocation = defaultServerInvocation;
    this.masterHandler = masterHandler;
    log.debug("Created channel-specific RpcServerHandler with shared components, serializer: {}", serializer.getType());
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    log.debug("Server handler added to channel: {}", ctx.channel());
    super.handlerAdded(ctx);
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    log.info("Server channelRead called with message of type: {}", msg.getClass().getName());
    super.channelRead(ctx, msg);
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    log.debug("Server channel active: {}", ctx.channel());
    this.context = ctx;
    super.channelActive(ctx);
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, ProtocolMsg msg) throws Exception {
    log.info("Server Channel[{}] channelRead0 received message: type={} (hex: 0x{}), contentLength={}, contentStart={}",
        ctx.channel().id().asShortText(), msg.getType(),
        Integer.toHexString(msg.getType() & 0xFF), msg.getContentLength(),
        msg.getContent() != null && msg.getContent().length > 0
            ? bytesToHex(msg.getContent(), 0, Math.min(10, msg.getContent().length)) + "..."
            : "empty");

    if (msg.getType() == RpcConstants.TYPE_REQUEST) {
      log.info("Server Channel[{}] Received REQUEST message, time: {}",
          ctx.channel().id().asShortText(), System.currentTimeMillis());

      byte[] requestBytes = msg.getContent();
      try {
        // 打印请求字节开头用于调试
        log.info("Server Request content start: {}",
            bytesToHex(requestBytes, 0, Math.min(20, requestBytes.length)));

        RpcRequest request = serializer.deserialize(requestBytes, RpcRequest.class);
        log.info("Server Channel[{}] Deserialized request: {}, method: {}",
            ctx.channel().id().asShortText(), request.getRequestId(), request.getMethodName());

        RpcResponse response = defaultServerInvocation.handleRequest(request);
        
        log.info("Server Channel[{}] Processed request: {}, created response {}",
            ctx.channel().id().asShortText(), request.getRequestId(), response);

        byte[] responseBytes = serializer.serialize(response);
        log.info("Server Channel[{}] Serialized response for request: {}, bytes length: {}, start: {}",
            ctx.channel().id().asShortText(), request.getRequestId(), responseBytes.length,
            bytesToHex(responseBytes, 0, Math.min(20, responseBytes.length)));

        // 使用新的便捷方法创建响应消息
        ProtocolMsg responseMsg = ProtocolMsg.responseFromBytes(responseBytes);
        log.info("Server Channel[{}] Created response message, type: {} (hex: 0x{}), contentLength: {}",
            ctx.channel().id().asShortText(), responseMsg.getType(),
            Integer.toHexString(responseMsg.getType() & 0xFF), responseMsg.getContentLength());

        log.info("Server Channel[{}] Sending response to client, request_id: {}, time: {}",
            ctx.channel().id().asShortText(), request.getRequestId(), System.currentTimeMillis());

        // 添加Listener来确认是否成功发送
        ctx.writeAndFlush(responseMsg).addListener(future -> {
          if (future.isSuccess()) {
            log.info("Server Channel[{}] Successfully sent response for request: {}, time: {}",
                ctx.channel().id().asShortText(), request.getRequestId(), System.currentTimeMillis());
          } else {
            log.error("Server Channel[{}] Failed to send response for request: {}, error: {}",
                ctx.channel().id().asShortText(), request.getRequestId(), future.cause().getMessage(), future.cause());
          }
        });
      } catch (Exception e) {
        log.error("Server Channel[{}] Error processing request: {}",
            ctx.channel().id().asShortText(), e.getMessage(), e);
      }
    } else {
      log.error("Server Channel[{}] Unknown message type: {} (hex: 0x{})",
          ctx.channel().id().asShortText(), msg.getType(),
          Integer.toHexString(msg.getType() & 0xFF));
    }
  }

  /**
   * 将字节数组转换为十六进制字符串
   */
  private String bytesToHex(byte[] bytes, int offset, int length) {
    StringBuilder sb = new StringBuilder();
    for (int i = offset; i < offset + length && i < bytes.length; i++) {
      sb.append(String.format("%02X ", bytes[i] & 0xFF));
    }
    return sb.toString();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    log.error("Server Channel[{}] RpcServerHandler exception", ctx.channel().id().asShortText(), cause);
    ctx.close();
  }

  public Serializer getSerializer() {
    return this.serializer;
  }

  public DefaultServerInvocation getDefaultServerInvocation() {
    return this.defaultServerInvocation;
  }
}