package com.spud.rpic.proxy;

import com.spud.rpic.common.domain.RpcRequest;
import com.spud.rpic.common.domain.RpcResponse;
import com.spud.rpic.common.exception.RpcException;
import com.spud.rpic.io.netty.client.invocation.ClientInvocation;
import com.spud.rpic.model.ServiceMetadata;
import lombok.extern.slf4j.Slf4j;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * @author Spud
 * @date 2025/2/15
 */
@Slf4j
public class RpcInvocationHandler implements MethodInterceptor {

	private final ClientInvocation clientInvocation;
	private final Class<?> interfaceClass;
	private final String serviceName;
	private final String version;
	private final String group;
	private final int timeout;

	public RpcInvocationHandler(ClientInvocation clientInvocation, Class<?> interfaceClass,
	                            String serviceName, String version, String group, int timeout) {
		this.clientInvocation = clientInvocation;
		this.interfaceClass = interfaceClass;
		this.serviceName = serviceName;
		this.version = version;
		this.group = group;
		this.timeout = timeout;
	}

	@Override
	public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) {
		// 1. 处理 Object 类的基础方法
		if (method.getDeclaringClass() == Object.class) {
			return handleObjectMethod(obj, method, args);
		}

		// 2. 构建RPC请求
		RpcRequest request = buildRequest(method, args);

		// 3. 构建服务元数据
		ServiceMetadata metadata = buildServiceMetadata();

		try {
			// 4. 检查返回类型是否为 CompletableFuture
			if (method.getReturnType() == java.util.concurrent.CompletableFuture.class) {
				return handleAsyncCall(metadata, request);
			} else {
				// 5. 同步调用
				return handleSyncCall(metadata, request);
			}
		} catch (Exception e) {
			log.error("Failed to invoke remote service: {}", metadata.getServiceId(), e);
			throw new RpcException("Failed to invoke remote service: " + metadata.getServiceId(), e);
		}
	}

	private Object handleSyncCall(ServiceMetadata metadata, RpcRequest request) throws Exception {
		RpcResponse response = clientInvocation.invoke(metadata, request, timeout);

		if (response.getError()) {
			throw new RpcException(response.getErrorMsg());
		}
		return response.getResult();
	}

	private Object handleAsyncCall(ServiceMetadata metadata, RpcRequest request) {
		try {
			return clientInvocation.invokeAsync(metadata, request, timeout)
				.thenApply(response -> {
					if (response.getError()) {
						throw new RpcException(response.getErrorMsg());
					}
					return response.getResult();
				});
		} catch (Exception e) {
			log.error("Failed to invoke async remote service: {}", metadata.getServiceId(), e);
			throw new RpcException("Failed to invoke async remote service: " + metadata.getServiceId(), e);
		}
	}

	private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
		String methodName = method.getName();
		switch (methodName) {
			case "toString":
				return String.format("Proxy[%s:%s]", serviceName, version);
			case "hashCode":
				return System.identityHashCode(proxy);
			case "equals":
				return proxy == args[0];
			default:
				throw new UnsupportedOperationException(
					String.format("Method %s is not supported", methodName));
		}
	}

	private RpcRequest buildRequest(Method method, Object[] args) {
		return RpcRequest.builder()
			.requestId(UUID.randomUUID().toString())
			.interfaceName(interfaceClass.getName())
			.interfaceClass(interfaceClass)
			.methodName(method.getName())
			.parameterTypes(method.getParameterTypes())
			.parameters(args)
			.group(group)
			.version(version)
			.build();
	}

	private ServiceMetadata buildServiceMetadata() {
		return ServiceMetadata.builder()
			.interfaceClass(interfaceClass)
			.interfaceName(interfaceClass.getName())
			.version(version)
			.group(group)
			.build();
	}
}