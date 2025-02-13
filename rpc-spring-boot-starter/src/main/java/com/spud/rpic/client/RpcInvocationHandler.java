package com.spud.rpic.client;

import com.spud.rpic.cluster.LoadBalancer;
import com.spud.rpic.common.domain.OrcRpcRequest;
import com.spud.rpic.common.domain.OrcRpcResponse;
import com.spud.rpic.common.exception.RpcException;
import com.spud.rpic.io.client.InvocationClient;
import com.spud.rpic.model.ServiceMetadata;
import com.spud.rpic.registry.Registry;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class RpcInvocationHandler implements InvocationHandler {

    private final ServiceMetadata serviceMetadata;
    private final ApplicationContext applicationContext;

    private RpcInvocationHandler(ServiceMetadata serviceMetadata, ApplicationContext applicationContext) {
        this.serviceMetadata = serviceMetadata;
        this.applicationContext = applicationContext;
    }

    public static Object newInstance(ServiceMetadata serviceMetadata, ApplicationContext applicationContext) {
        return Proxy.newProxyInstance(
                serviceMetadata.getServiceInterface().getClassLoader(),
                new Class[]{serviceMetadata.getServiceInterface()},
                new RpcInvocationHandler(serviceMetadata, applicationContext)
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        OrcRpcRequest request = new OrcRpcRequest();
        request.setServiceName(serviceMetadata.getServiceName());
        request.setMethodName(method.getName());
        request.setParameterTypes(method.getParameterTypes());
        request.setParameters(args);
        request.setRequestId(Long.parseLong(UUID.randomUUID().toString()));

        Registry registry = applicationContext.getBean(Registry.class);
        LoadBalancer loadBalancer = applicationContext.getBean(LoadBalancer.class);
        InvocationClient invocationClient = applicationContext.getBean(InvocationClient.class);

        try {
            OrcRpcResponse response = invocationClient.invoke(request, registry, loadBalancer, serviceMetadata);
            if (response.getException()!= null) {
                throw response.getException();
            }
            return response.getResult();
        } catch (Exception e) {
            throw new RpcException("RPC call failed", e);
        }
    }
}