package com.spud.rpic.io.netty;

import com.spud.rpic.io.serializer.Serializer;
import com.spud.rpic.io.server.ServerServiceInvocation;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class RpcServerInitializer extends ChannelInitializer<SocketChannel> {

    private final Serializer serializer;
    private final ServerServiceInvocation serverServiceInvocation;

    public RpcServerInitializer(Serializer serializer, ServerServiceInvocation serverServiceInvocation) {
        this.serializer = serializer;
        this.serverServiceInvocation = serverServiceInvocation;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new OrcProtocolDecoder());
        pipeline.addLast(new OrcProtocolEncoder());
        pipeline.addLast(new RpcServerHandler(serializer, serverServiceInvocation));
    }
}