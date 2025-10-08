package com.spud.rpic.proxy;

import com.spud.rpic.annotation.RpcReference;
import com.spud.rpic.io.netty.client.invocation.ClientInvocation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import org.springframework.stereotype.Component;

/**
 * @author Spud
 * @date 2025/2/16
 */
@Component
public class CglibProxyFactory implements ProxyFactory {

	private final Map<Class<?>, Object> proxyCache = new ConcurrentHashMap<>();

	private ClientInvocation clientInvocation;

	public CglibProxyFactory(ClientInvocation clientInvocation) {
		this.clientInvocation = clientInvocation;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T createProxy(Class<T> interfaceClass, RpcReference reference) {
		return (T) proxyCache.computeIfAbsent(interfaceClass, key -> {
			Enhancer enhancer = new Enhancer();
			enhancer.setSuperclass(interfaceClass);
			enhancer.setCallback(createInterceptor(interfaceClass, reference));
			return enhancer.create();
		});
	}

	private MethodInterceptor createInterceptor(Class<?> interfaceClass, RpcReference reference) {
		return new RpcInvocationHandler(
			clientInvocation,
			interfaceClass,
			reference.interfaceName(),
			reference.version(),
			reference.group(),
			reference.timeout());
	}

}