package com.spud.rpic.io.client;

import com.spud.rpic.io.serializer.Serializer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOutboundInvoker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Spud
 * @date 2025/2/13
 */
public class NetClientPool {
    private Serializer serializer;
    private Map<String, Channel> channelPool = new ConcurrentHashMap<>();

    public Channel getChannel(String address) {
        return channelPool.computeIfAbsent(address, addr -> {
            String[] parts = addr.split(":");
//            return NetClient.connect(parts[0], Integer.parseInt(parts[1]), serializer);
            return NetClient.connect(address);
        });
    }

    public void shutdown() {
        channelPool.values().forEach(ChannelOutboundInvoker::close);
    }
}