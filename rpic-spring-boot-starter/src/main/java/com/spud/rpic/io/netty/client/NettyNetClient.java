package com.spud.rpic.io.netty.client;

import com.spud.rpic.common.constants.RpcConstants;
import com.spud.rpic.common.domain.RpcRequest;
import com.spud.rpic.common.domain.RpcResponse;
import com.spud.rpic.common.exception.RpcException;
import com.spud.rpic.io.common.ProtocolMsg;
import com.spud.rpic.io.netty.NetClient;
import com.spud.rpic.model.ServiceURL;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Slf4j
public class NettyNetClient implements NetClient, InitializingBean, DisposableBean {

  private final ConnectionPool connectionPool;

  private final RpcClientHandler clientHandler;

  public NettyNetClient(ConnectionPool connectionPool, RpcClientHandler clientHandler) {
    this.connectionPool = connectionPool;
    this.clientHandler = clientHandler;
  }

  @Override
  public RpcResponse send(ServiceURL serviceURL, RpcRequest request, int timeout) throws Exception {
    log.debug("Sending request to {}, request: {}", serviceURL, request.getRequestId());

    // 验证服务URL
    if (serviceURL == null) {
      throw new RpcException("ServiceURL cannot be null");
    }
    if (serviceURL.getHost() == null || serviceURL.getHost().isEmpty()) {
      throw new RpcException("Service host cannot be null or empty: " + serviceURL);
    }

    // 1. 获取连接
    log.debug("Acquiring channel for {}", serviceURL);
    final Channel channel = connectionPool.acquireChannel(serviceURL);

    try {
      // 2. 创建Promise并注册
      Promise<RpcResponse> promise = channel.eventLoop().newPromise();
      clientHandler.addPromise(request.getRequestId(), promise, channel.eventLoop(), timeout);

      try {
        // 3. 序列化请求
        byte[] requestBytes = clientHandler.getSerializer().serialize(request);

        // 4. 创建协议消息对象
        ProtocolMsg protocolMsg = ProtocolMsg.fromBytes(requestBytes, RpcConstants.TYPE_REQUEST);

        log.debug("Creating ByteBuf for request: {}, type: {} (hex: 0x{}), content length: {}",
            request.getRequestId(), protocolMsg.getType(),
            Integer.toHexString(protocolMsg.getType() & 0xFF), protocolMsg.getContentLength());

        // 5. 手动创建ByteBuf并按协议格式写入
        ByteBuf byteBuf = channel.alloc().buffer(ProtocolMsg.HEADER_LENGTH + protocolMsg.getContentLength());
        byteBuf.writeByte(protocolMsg.getMagicNumber());
        byteBuf.writeByte(protocolMsg.getVersion());
        byteBuf.writeByte(protocolMsg.getType());
        byteBuf.writeInt(protocolMsg.getContentLength());

        // 写入消息体
        if (protocolMsg.getContent() != null && protocolMsg.getContentLength() > 0) {
          byteBuf.writeBytes(protocolMsg.getContent());
        }

        log.debug("Sending ByteBuf to channel: {}, request: {}, buffer size: {}",
            channel, request.getRequestId(), byteBuf.readableBytes());

        // 6. 直接发送ByteBuf
        channel.writeAndFlush(byteBuf).addListener(future -> {
          if (!future.isSuccess()) {
            log.error("Failed to write request: {}, error: {}",
                request.getRequestId(), future.cause().getMessage());
            clientHandler.removePromise(request.getRequestId());
            promise.tryFailure(future.cause());
            connectionPool.releaseChannel(serviceURL, channel);
          } else {
            log.debug("Request sent successfully: {}", request.getRequestId());
          }
        });

        // 7. 等待响应
        log.debug("Waiting for response for request: {}", request.getRequestId());
        RpcResponse response = promise.get(timeout, TimeUnit.MILLISECONDS);
        log.debug("Received response for request: {}", request.getRequestId());
        return response;
      } finally {
        // 8. 清理资源
        clientHandler.removePromise(request.getRequestId());
        connectionPool.releaseChannel(serviceURL, channel);
      }
    } catch (TimeoutException e) {
      log.error("Request timeout for service: {}, request: {}",
          serviceURL.getAddress(), request.getRequestId());
      throw new com.spud.rpic.common.exception.TimeoutException(
          "Request timeout for service: " + serviceURL.getAddress() + ", request: "
              + request.getRequestId());
    } catch (Exception e) {
      log.error("Failed to send request to {}, request: {}, error: {}",
          serviceURL, request.getRequestId(), e.getMessage());
      throw e;
    }
  }

  @Override
  public CompletableFuture<RpcResponse> sendAsync(ServiceURL serviceUrl, RpcRequest request,
      int timeout) {
    log.debug("Sending async request to {}, request: {}", serviceUrl, request.getRequestId());

    CompletableFuture<RpcResponse> future = new CompletableFuture<>();

    try {
      // 验证服务URL
      if (serviceUrl == null) {
        throw new RpcException("ServiceURL cannot be null");
      }
      if (serviceUrl.getHost() == null || serviceUrl.getHost().isEmpty()) {
        throw new RpcException("Service host cannot be null or empty: " + serviceUrl);
      }

      // 1. 获取连接
      connectionPool.acquireChannelAsync(serviceUrl).thenAccept(channel -> {
        try {
          // 2. 创建Promise并注册
          Promise<RpcResponse> promise = channel.eventLoop().newPromise();
          clientHandler.addPromise(request.getRequestId(), promise, channel.eventLoop(), timeout);

          // 3. 序列化请求
          byte[] requestBytes = clientHandler.getSerializer().serialize(request);

          // 4. 创建协议消息对象
          ProtocolMsg protocolMsg = ProtocolMsg.fromBytes(requestBytes, RpcConstants.TYPE_REQUEST);

          log.debug("Creating ByteBuf for async request: {}, type: {} (hex: 0x{}), content length: {}",
              request.getRequestId(), protocolMsg.getType(),
              Integer.toHexString(protocolMsg.getType() & 0xFF), protocolMsg.getContentLength());

          // 5. 手动创建ByteBuf并按协议格式写入
          ByteBuf byteBuf = channel.alloc().buffer(ProtocolMsg.HEADER_LENGTH + protocolMsg.getContentLength());
          byteBuf.writeByte(protocolMsg.getMagicNumber());
          byteBuf.writeByte(protocolMsg.getVersion());
          byteBuf.writeByte(protocolMsg.getType());
          byteBuf.writeInt(protocolMsg.getContentLength());

          // 写入消息体
          if (protocolMsg.getContent() != null && protocolMsg.getContentLength() > 0) {
            byteBuf.writeBytes(protocolMsg.getContent());
          }

          log.debug("Sending async ByteBuf to channel: {}, request: {}, buffer size: {}",
              channel, request.getRequestId(), byteBuf.readableBytes());

          // 6. 直接发送ByteBuf
          channel.writeAndFlush(byteBuf).addListener(writeFuture -> {
            if (!writeFuture.isSuccess()) {
              log.error("Failed to send async request: {}, error: {}",
                  request.getRequestId(), writeFuture.cause().getMessage());
              clientHandler.removePromise(request.getRequestId());
              future.completeExceptionally(writeFuture.cause());
              connectionPool.releaseChannel(serviceUrl, channel);
            } else {
              log.debug("Async request sent successfully: {}", request.getRequestId());
            }
          });

          // 7. 设置Promise完成回调
          promise.addListener((GenericFutureListener<Future<RpcResponse>>) promiseFuture -> {
            if (promiseFuture.isSuccess()) {
              log.debug("Received async response for request: {}", request.getRequestId());
              future.complete(promiseFuture.getNow());
            } else {
              log.error("Async request failed: {}, error: {}",
                  request.getRequestId(), promiseFuture.cause().getMessage());
              future.completeExceptionally(promiseFuture.cause());
            }
            connectionPool.releaseChannel(serviceUrl, channel);
          });

        } catch (Exception e) {
          log.error("Error processing async request: {}, error: {}",
              request.getRequestId(), e.getMessage());
          clientHandler.removePromise(request.getRequestId());
          future.completeExceptionally(e);
          connectionPool.releaseChannel(serviceUrl, channel);
        }
      }).exceptionally(e -> {
        log.error("Failed to acquire channel for async request: {}, error: {}",
            request.getRequestId(), e.getMessage());
        future.completeExceptionally(new RpcException("Failed to acquire channel", e));
        return null;
      });
    } catch (Exception e) {
      log.error("Failed to send async request: {}, error: {}",
          request.getRequestId(), e.getMessage());
      future.completeExceptionally(e);
    }

    return future;
  }

  @Override
  public void close() {
    connectionPool.close();
  }

  @Override
  public void destroy() throws Exception {
    this.close();
    clientHandler.close();
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    // 初始化逻辑
  }
}