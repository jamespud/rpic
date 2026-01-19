package com.spud.rpic.util;

import com.spud.rpic.annotation.RpcReference;
import com.spud.rpic.annotation.RpcService;
import com.spud.rpic.model.ServiceMetadata;
import java.lang.reflect.Method;
import org.springframework.util.StringUtils;

/**
 * @author Spud
 * @date 2025/2/26
 */
public class CommonUtils {

	public static ServiceMetadata buildServiceMetadata(RpcService annotation, Object bean) {
		// 获取接口名称
		String interfaceName;
		if (StringUtils.hasText(annotation.interfaceName())) {
			interfaceName = annotation.interfaceName();
		} else {
			Class<?>[] interfaces = bean.getClass().getInterfaces();
			if (interfaces != null && interfaces.length > 0) {
				interfaceName = interfaces[0].getName();
			} else {
				throw new IllegalArgumentException("@RpcService missing interface and no implemented interfaces found on bean: " + bean.getClass().getName());
			}
		}

		// 获取接口类
		Class<?> interfaceClass;
		if (annotation.interfaceClass() != void.class) {
			interfaceClass = annotation.interfaceClass();
		} else {
			Class<?>[] interfaces = bean.getClass().getInterfaces();
			if (interfaces != null && interfaces.length > 0) {
				interfaceClass = interfaces[0];
			} else {
				throw new IllegalArgumentException("@RpcService missing interface class and no implemented interfaces found on bean: " + bean.getClass().getName());
			}
		}

		return ServiceMetadata.builder()
			.interfaceName(interfaceName)
			.interfaceClass(interfaceClass)
			.version(StringUtils.hasText(annotation.version()) ? annotation.version() : "1.0.0")
			.group(annotation.group())
			.weight(annotation.weight() <= 0 ? 1 : annotation.weight())
			.protocol("rpic")
			.build();
	}

	public static ServiceMetadata buildMethodServiceMetadata(RpcService annotation, Object bean,
		Method method) {
		return ServiceMetadata.builder()
			.interfaceName(getMethodServiceName(annotation, method))
			.interfaceClass(method.getDeclaringClass())
			.version(annotation.version())
			.weight(annotation.weight())
			.protocol("rpic")
			.build();
	}

	private static String getServiceName(RpcService annotation, Object bean) {
		if (StringUtils.hasText(annotation.interfaceName())) {
			return annotation.interfaceName();
		}
		Class<?>[] interfaces = bean.getClass().getInterfaces();
		if (interfaces != null && interfaces.length > 0) {
			return interfaces[0].getName();
		}
		throw new IllegalArgumentException("@RpcService missing interface and no implemented interfaces found on bean: " + bean.getClass().getName());
	}

	private static String getMethodServiceName(RpcService annotation, Method method) {
		return StringUtils.hasText(annotation.interfaceName())
			? annotation.interfaceName()
			: method.getDeclaringClass().getName() + "#" + method.getName();
	}

	private static Class<?> getServiceInterface(RpcService annotation, Object bean) {
		if (annotation.interfaceClass() != void.class) {
			return annotation.interfaceClass();
		}
		Class<?>[] interfaces = bean.getClass().getInterfaces();
		if (interfaces != null && interfaces.length > 0) {
			return interfaces[0];
		}
		throw new IllegalArgumentException("@RpcService missing interface class and no implemented interfaces found on bean: " + bean.getClass().getName());
	}

	public static ServiceMetadata buildConsumerMetadata(RpcReference reference, Class<?> type) {
		// 确保interfaceName不为空
		String interfaceName = reference.interfaceName();
		if (!StringUtils.hasText(interfaceName) && type != null) {
			interfaceName = type.getName();
		}

		// 确保version不为空
		String version = reference.version();
		if (!StringUtils.hasText(version)) {
			version = "1.0.0";
		}

		// 确保group不为空
		String group = reference.group();
		if (group == null) {
			group = "";
		}

		return ServiceMetadata.builder()
			.interfaceName(interfaceName)
			.interfaceClass(type)
			.version(version)
			.group(group)
			.protocol("rpic")
			.build();
	}
}
