package com.spud.rpic.io.netty;

import com.spud.rpic.io.serializer.Serializer;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class RpcClientInitializer extends ChannelInitializer<SocketChannel> {

    private final Serializer serializer;

    public RpcClientInitializer(Serializer serializer) {
        this.serializer = serializer;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new OrcProtocolDecoder());
        pipeline.addLast(new OrcProtocolEncoder());
        pipeline.addLast(new RpcClientHandler(serializer));
    }
}