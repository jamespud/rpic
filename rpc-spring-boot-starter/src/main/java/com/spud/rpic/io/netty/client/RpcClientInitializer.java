package com.spud.rpic.io.netty.client;

import com.spud.rpic.io.netty.ProtocolDecoder;
import com.spud.rpic.io.netty.ProtocolEncoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class RpcClientInitializer extends ChannelInitializer<SocketChannel> {

    private final RpcClientHandler rpcClientHandler;

    public RpcClientInitializer(RpcClientHandler rpcClientHandler) {
        this.rpcClientHandler = rpcClientHandler;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new ProtocolDecoder());
        pipeline.addLast(new ProtocolEncoder());
        pipeline.addLast(this.rpcClientHandler);
    }
}