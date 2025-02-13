package com.spud.rpic.io.client;

import com.spud.rpic.common.constants.RpcConstants;
import com.spud.rpic.common.domain.OrcRpcRequest;
import com.spud.rpic.common.domain.OrcRpcResponse;
import com.spud.rpic.io.common.ProtocolMsg;
import com.spud.rpic.model.ServiceMetadata;
import com.spud.rpic.io.netty.RpcClientHandler;
import com.spud.rpic.model.ServiceURL;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Promise;
import com.spud.rpic.registry.Registry;
import com.spud.rpic.cluster.LoadBalancer;
import com.spud.rpic.io.serializer.Serializer;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class InvocationClient {

    private final Serializer serializer;

    public InvocationClient(Serializer serializer) {
        this.serializer = serializer;
    }

    public OrcRpcResponse invoke(OrcRpcRequest request, Registry registry, LoadBalancer loadBalancer,
                                 ServiceMetadata serviceMetadata) throws Exception {
        List<ServiceURL> serviceURLs = registry.discover(serviceMetadata);
        if (serviceURLs.isEmpty()) {
            throw new RuntimeException("No available service instances");
        }
        ServiceURL serviceURL = loadBalancer.select(serviceURLs);

        Channel channel = NetClient.connect(serviceURL.getServiceAddress());
        if (channel == null) {
            throw new RuntimeException("Failed to connect to service");
        }

        ProtocolMsg protocolMsg = new ProtocolMsg();
        protocolMsg.setMagicNumber(RpcConstants.MAGIC_NUMBER);
        protocolMsg.setVersion(RpcConstants.VERSION);
        protocolMsg.setType((byte) 0);
        byte[] requestBytes = serializer.serialize(request);
        protocolMsg.setContentLength(requestBytes.length);
        protocolMsg.setContent(requestBytes);

        Promise<OrcRpcResponse> promise = new DefaultPromise<>(channel.eventLoop());
        channel.writeAndFlush(protocolMsg).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                // 这里添加对响应的处理逻辑，等待响应并处理
                channel.pipeline().get(RpcClientHandler.class).addPromise(request.getRequestId(), promise);
            } else {
                promise.setFailure(future.cause());
            }
        });

        return promise.get(5, TimeUnit.SECONDS);
    }
}