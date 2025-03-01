package com.spud.rpic.io.netty.server;

import com.spud.rpic.common.constants.RpcConstants;
import com.spud.rpic.common.domain.RpcRequest;
import com.spud.rpic.common.domain.RpcResponse;
import com.spud.rpic.io.common.ProtocolMsg;
import com.spud.rpic.io.netty.server.invocation.DefaultServerInvocation;
import com.spud.rpic.io.serializer.Serializer;
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

    public RpcServerHandler(Serializer serializer, DefaultServerInvocation defaultServerInvocation) {
        this.serializer = serializer;
        this.defaultServerInvocation = defaultServerInvocation;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProtocolMsg msg) throws Exception {
        switch (msg.getType()) {
            case RpcConstants.TYPE_REQUEST:
                byte[] requestBytes = msg.getContent();
                RpcRequest request = serializer.deserialize(requestBytes, RpcRequest.class);
                RpcResponse response = defaultServerInvocation.handleRequest(request);
                byte[] responseBytes = serializer.serialize(response);
                ProtocolMsg responseMsg = ProtocolMsg.fromBytes(responseBytes);

                ctx.writeAndFlush(responseMsg);
                break;
            default:
                log.error("Unknown message type: {}", msg.getType());
                break;
        }
    }
}