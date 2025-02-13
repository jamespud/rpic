package com.spud.rpic.io.server;

import com.spud.rpic.common.domain.OrcRpcRequest;
import com.spud.rpic.common.domain.OrcRpcResponse;
import com.spud.rpic.common.exception.RpcException;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class ServerServiceInvocation implements ApplicationContextAware {
    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public OrcRpcResponse handleRequest(OrcRpcRequest request) {
        OrcRpcResponse response = new OrcRpcResponse();
        response.setRequestId(request.getRequestId());
        try {
            Object serviceBean = applicationContext.getBean(request.getServiceName());
            Method method = serviceBean.getClass().getMethod(request.getMethodName(), request.getParameterTypes());
            Object result = method.invoke(serviceBean, request.getParameters());
            response.setResult(result);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            response.setException(new RpcException("Failed to invoke method", e));
        } catch (BeansException e) {
            response.setException(new RpcException("Service not found", e));
        }
        return response;
    }
}