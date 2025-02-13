package com.spud.rpic.io.netty;

import com.spud.rpic.common.constants.RpcConstants;
import com.spud.rpic.common.domain.OrcRpcRequest;
import com.spud.rpic.common.domain.OrcRpcResponse;
import com.spud.rpic.io.common.ProtocolMsg;
import com.spud.rpic.io.serializer.Serializer;
import com.spud.rpic.io.server.ServerServiceInvocation;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class RpcServerHandler extends SimpleChannelInboundHandler<ProtocolMsg> {

    private final Serializer serializer;
    private final ServerServiceInvocation serverServiceInvocation;

    public RpcServerHandler(Serializer serializer, ServerServiceInvocation serverServiceInvocation) {
        this.serializer = serializer;
        this.serverServiceInvocation = serverServiceInvocation;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProtocolMsg msg) throws Exception {
        if (msg.getType() == 0) {
            byte[] requestBytes = msg.getContent();
            OrcRpcRequest request = serializer.deserialize(requestBytes, OrcRpcRequest.class);
            OrcRpcResponse response = serverServiceInvocation.handleRequest(request);
            byte[] responseBytes = serializer.serialize(response);

            ProtocolMsg responseMsg = new ProtocolMsg();
            responseMsg.setMagicNumber(RpcConstants.MAGIC_NUMBER);
            responseMsg.setVersion(RpcConstants.VERSION);
            responseMsg.setType((byte) 1);
            responseMsg.setContentLength(responseBytes.length);
            responseMsg.setContent(responseBytes);

            ctx.writeAndFlush(responseMsg);
        }
    }
}