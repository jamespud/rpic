package com.spud.rpic.io.netty.server.invocation;

import com.spud.rpic.common.domain.RpcRequest;
import com.spud.rpic.common.domain.RpcResponse;
import com.spud.rpic.common.exception.RpcException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Slf4j
public class DefaultServerInvocation implements ServerInvocation, ApplicationContextAware {

	private ApplicationContext applicationContext;

	private final Map<String, Method> methodCache = new ConcurrentHashMap<>();

	private final int maxConcurrentRequests = 100;

	private final Semaphore semaphore = new Semaphore(maxConcurrentRequests);

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@Override
	public RpcResponse handleRequest(RpcRequest request) {
		RpcResponse response = new RpcResponse();
		response.setRequestId(request.getRequestId());

		if (!semaphore.tryAcquire()) {
			response.setError(true);
			response.setErrorMsg("Server is overloaded");
			return response;
		}

		try {
			return doHandleRequest(request);
		} finally {
			semaphore.release();
		}
	}

	private RpcResponse doHandleRequest(RpcRequest request) {
		RpcResponse response = new RpcResponse();
		response.setRequestId(request.getRequestId());

		validateRequest(request);

		try {
			Class<?> interfaceClass = request.getInterfaceClass();
			Object serviceBean = applicationContext.getBean(interfaceClass);
			String methodKey = request.getServiceKey();
			Method method = methodCache.computeIfAbsent(methodKey, k -> {
				try {
					return serviceBean.getClass().getMethod(
						request.getMethodName(),
						request.getParameterTypes()
					);
				} catch (NoSuchMethodException e) {
					throw new RpcException("Method not found: " + methodKey, e);
				}
			});
			Object result = method.invoke(serviceBean, request.getParameters());
			response.setResult(result);
		} catch (IllegalAccessException e) {
			response.setError(true);
			response.setErrorMsg("Method access error: " + e.getMessage());
			log.error("Method access error", e);
		} catch (InvocationTargetException e) {
			response.setError(true);
			response.setErrorMsg("Method invocation error: " + e.getTargetException().getMessage());
			log.error("Method invocation error", e.getTargetException());
		} catch (BeansException e) {
			response.setError(true);
			response.setErrorMsg("Service not found: " + e.getMessage());
			log.error("Service not found", e);
		} catch (Exception e) {
			response.setError(true);
			response.setErrorMsg("Internal server error: " + e.getMessage());
			log.error("Internal server error", e);
		}
		response.setError(false);
		return response;
	}

	private void validateRequest(RpcRequest request) {
		if (request.getInterfaceClass() == null) {
			throw new IllegalArgumentException("Service interface cannot be null");
		}
		if (!StringUtils.hasLength(request.getMethodName())) {
			throw new IllegalArgumentException("Method name cannot be empty");
		}
	}
}