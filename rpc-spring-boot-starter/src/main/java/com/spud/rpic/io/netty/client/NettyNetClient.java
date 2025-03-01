package com.spud.rpic.io.netty.client;

import com.spud.rpic.common.domain.RpcRequest;
import com.spud.rpic.common.domain.RpcResponse;
import com.spud.rpic.common.exception.RpcException;
import com.spud.rpic.io.common.ProtocolMsg;
import com.spud.rpic.io.netty.NetClient;
import com.spud.rpic.model.ServiceURL;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class NettyNetClient implements NetClient, InitializingBean, DisposableBean {

    private final ConnectionPool connectionPool;

    private final RpcClientHandler clientHandler;

    public NettyNetClient(ConnectionPool connectionPool, RpcClientHandler clientHandler) {
        this.connectionPool = connectionPool;
        this.clientHandler = clientHandler;
    }

    @Override
    public RpcResponse send(ServiceURL serviceURL, RpcRequest request, int timeout) throws Exception {
        Channel channel = null;
        String address = serviceURL.getAddress();

        try {
            // 1. 获取连接
            channel = connectionPool.acquireChannel(serviceURL);
            // 2. 构建协议消息
            ProtocolMsg msg = clientHandler.buildProtocolMsg(request);
            // 3. 创建Promise并注册
            Promise<RpcResponse> promise = channel.eventLoop().newPromise();
            clientHandler.addPromise(request.getRequestId(), promise, timeout);
            // 4. 发送请求
            channel.writeAndFlush(msg).addListener(future -> {
                if (!future.isSuccess()) {
                    clientHandler.removePromise(request.getRequestId());
                    promise.tryFailure(future.cause());
                }
            });
            // 5. 等待响应
            return promise.get(timeout, TimeUnit.MILLISECONDS);
        } finally {
            if (channel != null) {
                clientHandler.removePromise(request.getRequestId());
                connectionPool.releaseChannel(serviceURL, channel);
            }
        }
    }

    @Override
    public CompletableFuture<RpcResponse> sendAsync(ServiceURL serviceUrl, RpcRequest request, int timeout) {
        CompletableFuture<RpcResponse> future = new CompletableFuture<>();

        try {
            // 1. 获取连接
            connectionPool.acquireChannelAsync(serviceUrl).thenAccept(channel -> {
                try {
                    // 2. 创建Promise并注册
                    Promise<RpcResponse> promise = channel.eventLoop().newPromise();
                    clientHandler.addPromise(request.getRequestId(), promise, timeout);

                    // 3. 构建协议消息
                    ProtocolMsg msg = clientHandler.buildProtocolMsg(request);

                    // 4. 发送请求
                    channel.writeAndFlush(msg).addListener(writeFuture -> {
                        if (!writeFuture.isSuccess()) {
                            clientHandler.removePromise(request.getRequestId());
                            future.completeExceptionally(writeFuture.cause());
                            connectionPool.releaseChannel(serviceUrl, channel);
                        }
                    });

                    // 5. 设置Promise完成回调
                    promise.addListener((GenericFutureListener<Future<RpcResponse>>) promiseFuture -> {
                        if (promiseFuture.isSuccess()) {
                            future.complete(promiseFuture.getNow());
                        } else {
                            future.completeExceptionally(promiseFuture.cause());
                        }
                        connectionPool.releaseChannel(serviceUrl, channel);
                    });

                } catch (Exception e) {
                    clientHandler.removePromise(request.getRequestId());
                    future.completeExceptionally(e);
                    connectionPool.releaseChannel(serviceUrl, channel);
                }
            }).exceptionally(e -> {
                future.completeExceptionally(new RpcException("Failed to acquire channel", e));
                return null;
            });
        } catch (Exception e) {
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

    }
}