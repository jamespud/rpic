package com.spud.rpic.io.netty.client;

import com.spud.rpic.common.constants.RpcConstants;
import com.spud.rpic.common.domain.RpcRequest;
import com.spud.rpic.common.domain.RpcResponse;
import com.spud.rpic.common.exception.RpcException;
import com.spud.rpic.io.common.ProtocolMsg;
import com.spud.rpic.io.netty.NetClient;
import com.spud.rpic.model.ServiceURL;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Promise;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.concurrent.CompletableFuture;

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
    if (serviceURL == null || serviceURL.getHost() == null || serviceURL.getHost().isEmpty()) {
      throw new RpcException("Invalid service URL: " + serviceURL);
    }

    // 1. 获取连接
    Channel channel = connectionPool.acquireChannel(serviceURL);
    if (!channel.isActive()) {
      connectionPool.releaseChannel(serviceURL, channel);
      throw new RpcException("Channel is not active");
    }

    try {
      // 2. 创建Promise并注册
      Promise<RpcResponse> promise = channel.eventLoop().newPromise();
      clientHandler.addPromise(request.getRequestId(), promise, channel.eventLoop(), timeout);

      // 3. 序列化请求
      byte[] requestBytes = clientHandler.getSerializer().serialize(request);

      // 4. 创建协议消息
      ProtocolMsg protocolMsg = ProtocolMsg.fromBytes(requestBytes);

      // 5. 发送消息
      channel.writeAndFlush(protocolMsg).addListener(future -> {
        if (!future.isSuccess()) {
          log.error("Failed to send request: {}", request.getRequestId());
          clientHandler.removePromise(request.getRequestId());
          promise.tryFailure(future.cause());
          connectionPool.releaseChannel(serviceURL, channel);
        } else {
          log.debug("Request sent successfully: {}", request.getRequestId());
        }
      });

      // 6. 等待响应
      return promise.get(timeout, java.util.concurrent.TimeUnit.MILLISECONDS);

    } finally {
      clientHandler.removePromise(request.getRequestId());
      connectionPool.releaseChannel(serviceURL, channel);
    }
  }

  @Override
  public CompletableFuture<RpcResponse> sendAsync(ServiceURL serviceUrl, RpcRequest request, int timeout) {
    CompletableFuture<RpcResponse> future = new CompletableFuture<>();

    try {
      // 验证服务URL
      if (serviceUrl == null || serviceUrl.getHost() == null || serviceUrl.getHost().isEmpty()) {
        throw new RpcException("Invalid service URL: " + serviceUrl);
      }

      // 1. 异步获取连接
      connectionPool.acquireChannelAsync(serviceUrl).thenAccept(channel -> {
        if (!channel.isActive()) {
          future.completeExceptionally(new RpcException("Channel is not active"));
          connectionPool.releaseChannel(serviceUrl, channel);
          return;
        }

        try {
          // 2. 创建Promise并注册
          Promise<RpcResponse> promise = channel.eventLoop().newPromise();
          clientHandler.addPromise(request.getRequestId(), promise, channel.eventLoop(), timeout);

          // 3. 序列化请求
          byte[] requestBytes = clientHandler.getSerializer().serialize(request);

          // 4. 创建协议消息
          ProtocolMsg protocolMsg = ProtocolMsg.fromBytes(requestBytes);

          // 5. 发送消息
          channel.writeAndFlush(protocolMsg).addListener(writeFuture -> {
            if (!writeFuture.isSuccess()) {
              log.error("Failed to send async request: {}", request.getRequestId());
              clientHandler.removePromise(request.getRequestId());
              future.completeExceptionally(writeFuture.cause());
              connectionPool.releaseChannel(serviceUrl, channel);
            } else {
              log.debug("Async request sent successfully: {}", request.getRequestId());
            }
          });

          // 6. 设置Promise完成回调
          promise.addListener(promiseFuture -> {
            try {
              if (promiseFuture.isSuccess()) {
                future.complete((RpcResponse) promiseFuture.getNow());
              } else {
                future.completeExceptionally(promiseFuture.cause());
              }
            } finally {
              clientHandler.removePromise(request.getRequestId());
              connectionPool.releaseChannel(serviceUrl, channel);
            }
          });

        } catch (Exception e) {
          log.error("Error processing async request: {}", request.getRequestId(), e);
          clientHandler.removePromise(request.getRequestId());
          future.completeExceptionally(e);
          connectionPool.releaseChannel(serviceUrl, channel);
        }
      }).exceptionally(e -> {
        log.error("Failed to acquire channel for async request: {}", request.getRequestId(), e);
        future.completeExceptionally(new RpcException("Failed to acquire channel", e));
        return null;
      });
    } catch (Exception e) {
      log.error("Failed to send async request: {}", request.getRequestId(), e);
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
  public void afterPropertiesSet() {
    // 初始化逻辑（如果需要）
  }
}