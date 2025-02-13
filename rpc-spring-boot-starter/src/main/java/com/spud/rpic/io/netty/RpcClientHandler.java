package com.spud.rpic.io.netty;

import com.spud.rpic.common.domain.OrcRpcResponse;
import com.spud.rpic.io.common.ProtocolMsg;
import com.spud.rpic.io.serializer.Serializer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.Promise;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class RpcClientHandler extends SimpleChannelInboundHandler<ProtocolMsg> {

    private final Serializer serializer;
    private final Map<String, Promise<OrcRpcResponse>> promiseMap = new HashMap<>();

    public RpcClientHandler(Serializer serializer) {
        this.serializer = serializer;
    }

    public void addPromise(String requestId, Promise<OrcRpcResponse> promise) {
        promiseMap.put(requestId, promise);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProtocolMsg msg) throws Exception {
        if (msg.getType() == 1) {
            byte[] responseBytes = msg.getContent();
            OrcRpcResponse response = serializer.deserialize(responseBytes, OrcRpcResponse.class);
            Promise<OrcRpcResponse> promise = promiseMap.remove(response.getRequestId());
            if (promise!= null) {
                promise.setSuccess(response);
            }
        }
    }
}