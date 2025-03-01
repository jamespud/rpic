package com.spud.rpic.io.netty.client;

import com.spud.rpic.common.constants.RpcConstants;
import com.spud.rpic.common.domain.RpcRequest;
import com.spud.rpic.common.domain.RpcResponse;
import com.spud.rpic.common.exception.RpcException;
import com.spud.rpic.common.exception.TimeoutException;
import com.spud.rpic.io.common.ProtocolMsg;
import com.spud.rpic.io.serializer.Serializer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Slf4j
public class RpcClientHandler extends SimpleChannelInboundHandler<ProtocolMsg> {

    private final Serializer serializer;
    private final int timeout = 5000;
    private final Map<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();

    public RpcClientHandler(Serializer serializer) {
        this.serializer = serializer;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProtocolMsg msg) throws Exception {
        if (msg.getType() == RpcConstants.TYPE_RESPONSE) {
            handleResponse(msg);
        } else if (msg.getType() == RpcConstants.TYPE_HEARTBEAT) {
            handleHeartbeat(ctx, msg);
        } else {
            log.warn("Unexpected message type: {}", msg.getType());
        }
    }

    public ProtocolMsg buildProtocolMsg(RpcRequest request) {
        byte[] requestBytes = serializer.serialize(request);
        return ProtocolMsg.fromBytes(requestBytes);
    }

    private void handleResponse(ProtocolMsg msg) {
        RpcResponse response = serializer.deserialize(msg.getContent(), RpcResponse.class);
        PendingRequest pendingRequest = pendingRequests.remove(response.getRequestId());

        if (pendingRequest != null) {
            // 取消超时任务
            if (pendingRequest.timeoutFuture != null) {
                pendingRequest.timeoutFuture.cancel(false);
            }
            // 设置响应结果
            pendingRequest.promise.setSuccess(response);
        } else {
            log.warn("Received response for unknown request: {}", response.getRequestId());
        }
    }

    private void handleHeartbeat(ChannelHandlerContext ctx, ProtocolMsg msg) {
        // 响应心跳
        // TODO: 
        ProtocolMsg heartbeatResponse = ProtocolMsg.heartBeat();
        ctx.writeAndFlush(heartbeatResponse);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("RpcClientHandler exception", cause);
        ctx.close();
        failAllPromises(cause);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.warn("Channel inactive: {}", ctx.channel());
        failAllPromises(new RpcException("Channel closed"));
    }

    public void addPromise(String requestId, Promise<RpcResponse> promise, int timeout) {
        // 创建超时任务
        ScheduledFuture<?> timeoutFuture = promise.executor().schedule(() -> {
            PendingRequest pendingRequest = pendingRequests.remove(requestId);
            if (pendingRequest != null && pendingRequest.promise.tryFailure(
                    new TimeoutException("Request timeout after " + timeout + "ms"))) {
                log.warn("Request timeout: {}", requestId);
            }
        }, timeout, TimeUnit.MILLISECONDS);

        // 保存请求信息
        pendingRequests.put(requestId, new PendingRequest(promise, timeoutFuture));
    }

    public void removePromise(String requestId) {
        PendingRequest pendingRequest = pendingRequests.remove(requestId);
        if (pendingRequest != null && pendingRequest.timeoutFuture != null) {
            pendingRequest.timeoutFuture.cancel(false);
        }
    }

    private void failAllPromises(Throwable cause) {
        pendingRequests.forEach((requestId, pendingRequest) ->
                pendingRequest.promise.tryFailure(new RpcException("Channel exception: " + cause.getMessage(), cause))
        );
        pendingRequests.clear();
    }

    public void close() {
        failAllPromises(new RpcException("Handler closed"));
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