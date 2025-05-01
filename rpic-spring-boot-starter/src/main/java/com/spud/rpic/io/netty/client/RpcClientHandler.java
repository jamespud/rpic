package com.spud.rpic.io.netty.client;

import com.spud.rpic.common.constants.RpcConstants;
import com.spud.rpic.common.domain.RpcRequest;
import com.spud.rpic.common.domain.RpcResponse;
import com.spud.rpic.common.exception.RpcException;
import com.spud.rpic.common.exception.TimeoutException;
import com.spud.rpic.io.common.ProtocolMsg;
import com.spud.rpic.io.serializer.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Slf4j
public class RpcClientHandler extends SimpleChannelInboundHandler<ProtocolMsg> {

  private final Serializer serializer;
  private final int timeout = 5000;
  // 共享pendingRequests，使所有handler实例都能访问请求记录
  private final Map<String, PendingRequest> pendingRequests;

  // 主handler实例，用于代理各channel处理器的请求记录
  private final RpcClientHandler masterHandler;
  private volatile ChannelHandlerContext context;

  /**
   * 创建主Handler实例（由Spring管理的单例）
   */
  public RpcClientHandler(Serializer serializer) {
    this.serializer = serializer;
    this.pendingRequests = new ConcurrentHashMap<>();
    this.masterHandler = null; // 自身就是主handler
    log.debug("Created master RpcClientHandler with serializer: {}", serializer.getType());
  }

  /**
   * 为每个Channel创建独立但共享状态的Handler实例
   * 
   * @param serializer    序列化器
   * @param masterHandler 主handler实例，用于共享pendingRequests
   */
  public RpcClientHandler(Serializer serializer, RpcClientHandler masterHandler) {
    this.serializer = serializer;
    this.masterHandler = masterHandler;
    this.pendingRequests = masterHandler.pendingRequests; // 共享pendingRequests，确保所有handler能访问同一个Map
    log.debug("Created channel-specific RpcClientHandler with shared state, serializer: {}", serializer.getType());
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    log.debug("Handler added to channel: {}", ctx.channel());
    super.handlerAdded(ctx);
  }

  @Override
  public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
    log.debug("Channel registered: {}", ctx.channel());
    super.channelRegistered(ctx);
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    log.debug("Channel active: {}", ctx.channel());
    this.context = ctx;
    super.channelActive(ctx);
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, ProtocolMsg msg) throws Exception {
    log.info("Channel[{}] channelRead0 received message: type={} (hex: 0x{}), contentLength={}, contentStart={}",
        ctx.channel().id().asShortText(), msg.getType(),
        Integer.toHexString(msg.getType() & 0xFF), msg.getContentLength(),
        msg.getContent() != null && msg.getContent().length > 0
            ? bytesToHex(msg.getContent(), 0, Math.min(10, msg.getContent().length)) + "..."
            : "empty");

    if (msg.getType() == RpcConstants.TYPE_RESPONSE) {
      log.info("Channel[{}] Received RESPONSE message, calling handleResponse",
          ctx.channel().id().asShortText());
      handleResponse(msg);
    } else if (msg.getType() == RpcConstants.TYPE_HEARTBEAT) {
      log.info("Channel[{}] Received HEARTBEAT message, sending response",
          ctx.channel().id().asShortText());
      handleHeartbeat(ctx, msg);
    } else {
      log.warn("Channel[{}] Unexpected message type: {} (hex: 0x{})",
          ctx.channel().id().asShortText(), msg.getType(),
          Integer.toHexString(msg.getType() & 0xFF));
    }
  }

  private void handleResponse(ProtocolMsg msg) {
    try {
      log.info("Starting to deserialize response from bytes, content length: {}", msg.getContentLength());
      if (msg.getContent() == null || msg.getContent().length == 0) {
        log.error("Response content is null or empty");
        return;
      }

      // 打印内容的前20个字节，帮助调试
      log.info("Response content start: {}", bytesToHex(msg.getContent(), 0, Math.min(20, msg.getContent().length)));

      RpcResponse response = serializer.deserialize(msg.getContent(), RpcResponse.class);
      log.info("Deserialized response for request: {}, response error: {}",
          response.getRequestId(), response.getError());

      PendingRequest pendingRequest = pendingRequests.remove(response.getRequestId());

      if (pendingRequest != null) {
        // 取消超时任务
        if (pendingRequest.timeoutFuture != null) {
          pendingRequest.timeoutFuture.cancel(false);
        }
        // 设置响应结果
        pendingRequest.promise.setSuccess(response);
        log.info("Successfully completed promise for request: {}", response.getRequestId());
      } else {
        log.warn("Received response for unknown request: {}, active requests: {}",
            response.getRequestId(), pendingRequests.keySet());
      }
    } catch (Exception e) {
      log.error("Error handling response message: {}", e.getMessage(), e);
    }
  }

  private void handleHeartbeat(ChannelHandlerContext ctx, ProtocolMsg msg) {
    // 响应心跳
    ProtocolMsg heartbeatResponse = ProtocolMsg.heartBeat();
    ctx.writeAndFlush(heartbeatResponse);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    log.error("RpcClientHandler exception in channel: {}", ctx.channel(), cause);
    ctx.close();
    // 不要在这里failAllPromises，这会影响其他通道的请求
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) {
    log.warn("Channel inactive: {}", ctx.channel());
    this.context = null;
    // 不要在这里failAllPromises，这会影响其他通道的请求
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

  /**
   * 添加请求Promise并设置超时任务
   * 
   * @param requestId 请求ID
   * @param promise   用于接收响应的Promise
   * @param eventLoop 调度超时任务的EventLoop
   * @param timeout   超时时间(毫秒)
   */
  public void addPromise(String requestId, Promise<RpcResponse> promise, EventLoop eventLoop, int timeout) {
    // 先清理之前可能存在的同ID请求
    removePromise(requestId);

    ScheduledFuture<?> timeoutFuture = null;
    try {
      if (eventLoop != null) {
        // 创建超时任务
        timeoutFuture = eventLoop.schedule(() -> {
          // 使用CAS操作原子性地移除，确保线程安全
          PendingRequest pendingRequest = pendingRequests.remove(requestId);
          if (pendingRequest != null) {
            // 创建自定义超时异常
            TimeoutException timeoutException = new TimeoutException(
                "Request timeout after " + timeout + "ms for request: " + requestId);

            // 尝试设置Promise失败状态
            if (pendingRequest.promise.tryFailure(timeoutException)) {
              log.warn("Request timeout: {}", requestId);
            }
          }
        }, timeout, TimeUnit.MILLISECONDS);
      } else {
        log.warn("Cannot schedule timeout task for request: {}, no EventLoop available", requestId);
      }
    } catch (Exception e) {
      log.error("Failed to schedule timeout task for request: {}", requestId, e);
      // 如果无法创建超时任务，我们仍继续请求，只是没有超时检查
    }

    // 保存请求信息到并发Map
    log.debug("Adding promise for request: {}, current pending requests: {}",
        requestId, pendingRequests.size());
    pendingRequests.put(requestId, new PendingRequest(promise, timeoutFuture));
  }

  /**
   * 为兼容性保留的方法，使用当前Context的EventLoop调度
   * 
   * @param requestId 请求ID
   * @param promise   用于接收响应的Promise
   * @param timeout   超时时间(毫秒)
   */
  public void addPromise(String requestId, Promise<RpcResponse> promise, int timeout) {
    EventLoop eventLoop = null;
    if (context != null) {
      eventLoop = context.channel().eventLoop();
    }
    addPromise(requestId, promise, eventLoop, timeout);
  }

  /**
   * 移除请求Promise并取消相关的超时任务
   * 
   * @param requestId 请求ID
   * @return 是否成功移除
   */
  public boolean removePromise(String requestId) {
    PendingRequest pendingRequest = pendingRequests.remove(requestId);
    if (pendingRequest != null) {
      // 安全地取消超时任务
      if (pendingRequest.timeoutFuture != null) {
        pendingRequest.timeoutFuture.cancel(false);
      }
      return true;
    }
    return false;
  }

  /**
   * 失败所有待处理的Promise（通常在严重错误时调用）
   * 注意：只有主Handler应该调用此方法
   */
  public void failAllPromises(Throwable cause) {
    if (masterHandler != null) {
      log.warn("Non-master handler attempted to fail all promises, ignoring");
      return;
    }

    log.warn("Failing all pending promises due to: {}", cause.getMessage());
    pendingRequests.forEach((requestId, pendingRequest) -> {
      // 取消所有超时任务
      if (pendingRequest.timeoutFuture != null) {
        pendingRequest.timeoutFuture.cancel(false);
      }
      // 设置所有Promise为失败
      pendingRequest.promise.tryFailure(
          new RpcException("Channel exception: " + cause.getMessage(), cause));
    });
    pendingRequests.clear();
  }

  public void close() {
    if (masterHandler == null) {
      failAllPromises(new RpcException("Handler closed"));
    }
  }

  public Serializer getSerializer() {
    return this.serializer;
  }

  private static class PendingRequest {
    final Promise<RpcResponse> promise;
    final ScheduledFuture<?> timeoutFuture;

    PendingRequest(Promise<RpcResponse> promise, ScheduledFuture<?> timeoutFuture) {
      this.promise = promise;
      this.timeoutFuture = timeoutFuture;
    }
  }
}