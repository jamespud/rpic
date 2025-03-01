package com.spud.rpic.io.netty.server;

import com.spud.rpic.io.netty.ProtocolDecoder;
import com.spud.rpic.io.netty.ProtocolEncoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class RpcServerInitializer extends ChannelInitializer<SocketChannel> {

    private final RpcServerHandler rpcServerHandler;

    public RpcServerInitializer(RpcServerHandler rpcServerHandler) {
        this.rpcServerHandler = rpcServerHandler;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new ProtocolDecoder());
        pipeline.addLast(new ProtocolEncoder());
        pipeline.addLast(rpcServerHandler);
    }
}