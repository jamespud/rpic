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
    log.debug("Received message: type={}, length={}", msg.getType(), msg.getContentLength());

    if (msg.getType() == RpcConstants.TYPE_RESPONSE) {
      try {
        RpcResponse response = serializer.deserialize(msg.getContent(), RpcResponse.class);
        String requestId = response.getRequestId();

        PendingRequest pendingRequest = pendingRequests.get(requestId);
        if (pendingRequest != null) {
          log.debug("Found pending request for response: {}", requestId);
          pendingRequest.promise.setSuccess(response);
        } else {
          log.warn("No pending request found for response: {}", requestId);
        }
      } catch (Exception e) {
        log.error("Failed to process response", e);
      }
    } else {
      log.warn("Received unexpected message type: {}", msg.getType());
    }
  }

  private void handleHeartbeat(ChannelHandlerContext ctx, ProtocolMsg msg) {
    // 响应心跳
    ProtocolMsg heartbeatResponse = ProtocolMsg.heartBeat();
    ctx.writeAndFlush(heartbeatResponse);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    log.error("Channel exception: {}", cause.getMessage());
    if (masterHandler == null) {
      failAllPromises(cause);
    }
    ctx.close();
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) {
    log.debug("Channel inactive: {}", ctx.channel());
    if (masterHandler == null) {
      failAllPromises(new RpcException("Channel closed"));
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

  /**
   * 添加请求Promise并设置超时任务
   * 
   * @param requestId 请求ID
   * @param promise   用于接收响应的Promise
   * @param eventLoop 调度超时任务的EventLoop
   * @param timeout   超时时间(毫秒)
   */
  public void addPromise(String requestId, Promise<RpcResponse> promise, EventLoop eventLoop, int timeout) {
    if (eventLoop == null) {
      log.warn("EventLoop is null, using default timeout");
      addPromise(requestId, promise, timeout);
      return;
    }

    ScheduledFuture<?> timeoutFuture = eventLoop.schedule(() -> {
      if (removePromise(requestId)) {
        String error = String.format("Request timeout after %dms, requestId: %s", timeout, requestId);
        promise.tryFailure(new TimeoutException(error));
      }
    }, timeout, TimeUnit.MILLISECONDS);

    pendingRequests.put(requestId, new PendingRequest(promise, timeoutFuture));
    log.debug("Added promise for request: {}, timeout: {}ms", requestId, timeout);
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
      log.warn("Non-master handler attempted to fail all promises");
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