package com.spud.rpic.client;

import com.spud.rpic.io.netty.RpcClientInitializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class RpcClient {
    private String host;
    private int port;
    public RpcClient(String host, int port) {
        this.host = host;
        this.port = port;
    }
    public ChannelFuture connect() {
        NioEventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new RpcClientInitializer( null));
            return b.connect(host, port);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}