package com.spud.rpic.config.bean;

import com.spud.rpic.annotation.RpcReference;
import com.spud.rpic.proxy.ProxyFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

/**
 * @author Spud
 * @date 2025/2/15
 */
@Slf4j
public class RpcReferenceAnnotationProcessor implements BeanPostProcessor {

	private final ProxyFactory proxyFactory;

	public RpcReferenceAnnotationProcessor(ProxyFactory proxyFactory) {
		this.proxyFactory = proxyFactory;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		ReflectionUtils.doWithFields(bean.getClass(), field -> {
			RpcReference reference = field.getAnnotation(RpcReference.class);
			if (reference != null) {
				processRpcReferenceField(bean, field, reference);
			}
		});
		return bean;
	}

	private void processRpcReferenceField(Object bean, Field field, RpcReference reference) {
		Class<?> interfaceType = field.getType();
		try {
			Object proxy = proxyFactory.createProxy(interfaceType, reference);
			field.setAccessible(true);
			field.set(bean, proxy);
		} catch (IllegalAccessException e) {
			log.error("Failed to set proxy for field: {}", field.getName(), e);
			throw new RuntimeException("Failed to set proxy for field: " + field.getName(), e);
		}
	}
}