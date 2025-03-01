package com.spud.rpic.io.netty.client.invocation;

import com.spud.rpic.cluster.LoadBalancer;
import com.spud.rpic.common.domain.RpcRequest;
import com.spud.rpic.common.domain.RpcResponse;
import com.spud.rpic.common.exception.RpcException;
import com.spud.rpic.common.exception.ServiceNotFoundException;
import com.spud.rpic.common.exception.TimeoutException;
import com.spud.rpic.io.netty.NetClient;
import com.spud.rpic.model.ServiceMetadata;
import com.spud.rpic.model.ServiceURL;
import com.spud.rpic.registry.Registry;
import lombok.extern.slf4j.Slf4j;

import javax.naming.ServiceUnavailableException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Slf4j
public class DefaultClientInvocation implements ClientInvocation {

    private final Registry registry;

    private final LoadBalancer loadBalancer;

    private final NetClient netClient;

    private final int retryTimes;

//    public DefaultClientInvocation(Registry registry, LoadBalancer loadBalancer, NettyNetClient netClient) {
//        this.registry = registry;
//        this.loadBalancer = loadBalancer;
//        this.netClient = netClient;
//    }

    public DefaultClientInvocation(Registry registry, LoadBalancer loadBalancer, NetClient netClient,
                                   int retryTimes) {
        this.registry = registry;
        this.loadBalancer = loadBalancer;
        this.netClient = netClient;
        this.retryTimes = retryTimes;
    }

    @Override
    public RpcResponse invoke(ServiceMetadata metadata, RpcRequest request, int timeout) throws Exception {
        List<ServiceURL> instances = registry.discover(metadata);
        if (instances == null || instances.isEmpty()) {
            throw new ServiceNotFoundException("No available instances for service: " + metadata.getServiceKey());
        }

        // 记录已尝试的实例，避免重复尝试
        List<ServiceURL> triedInstances = new ArrayList<>();
        Exception lastException = null;

        // 重试机制
        for (int i = 0; i <= retryTimes; i++) {
            try {
                // 负载均衡选择服务实例，排除已尝试失败的实例
                ServiceURL selected = loadBalancer.select(instances, triedInstances);
                if (selected == null) {
                    throw new ServiceUnavailableException("No available instance after " + i + " retries");
                }

                triedInstances.add(selected);

                if (log.isDebugEnabled()) {
                    log.debug("Invoking service: {}, method: {}, instance: {}, retry: {}",
                            metadata.getServiceKey(), request.getMethodName(), selected.getAddress(), i);
                }

                // 发送请求
                return netClient.send(selected, request, timeout);
            } catch (TimeoutException e) {
                lastException = e;
                log.warn("Service invocation timeout, retry: {}/{}", i, retryTimes);
            } catch (Exception e) {
                lastException = e;
                log.warn("Service invocation failed, retry: {}/{}, error: {}", i, retryTimes, e.getMessage());
            }
        }
        throw new RpcException("Service invocation failed after " + retryTimes + " retries", lastException);

    }
}