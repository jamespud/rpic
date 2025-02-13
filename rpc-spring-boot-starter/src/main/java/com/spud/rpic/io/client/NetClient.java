package com.spud.rpic.io.client;

import com.spud.rpic.io.netty.RpcClientInitializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class NetClient {

    private static final NioEventLoopGroup GROUP = new NioEventLoopGroup();

    public static Channel connect(String address) {
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(GROUP)
                    .channel(NioSocketChannel.class)
                    .remoteAddress(new InetSocketAddress(address.split(":")[0], Integer.parseInt(address.split(":")[1])))
                    .handler(new RpcClientInitializer());

            ChannelFuture future = bootstrap.connect().sync();
            return future.channel();
        } catch (InterruptedException e) {
            GROUP.shutdownGracefully();
            return null;
        }
    }
}