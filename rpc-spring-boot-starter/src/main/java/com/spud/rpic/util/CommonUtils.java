package com.spud.rpic.util;

import com.spud.rpic.annotation.RpcReference;
import com.spud.rpic.annotation.RpcService;
import com.spud.rpic.model.ServiceMetadata;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;

/**
 * @author Spud
 * @date 2025/2/26
 */
public class CommonUtils {

    public static ServiceMetadata buildServiceMetadata(RpcService annotation, Object bean) {
        return ServiceMetadata.builder()
                .interfaceName(getServiceName(annotation, bean))
                .interfaceClass(getServiceInterface(annotation, bean))
                .version(annotation.version())
                .weight(annotation.weight())
                .build();
    }

    public static ServiceMetadata buildMethodServiceMetadata(RpcService annotation, Object bean, Method method) {
        return ServiceMetadata.builder()
                .interfaceName(getMethodServiceName(annotation, method))
                .interfaceClass(method.getDeclaringClass())
                .version(annotation.version())
                .weight(annotation.weight())
                .build();
    }

    private static String getServiceName(RpcService annotation, Object bean) {
        return StringUtils.hasText(annotation.interfaceName())
                ? annotation.interfaceName()
                : bean.getClass().getInterfaces()[0].getName();
    }

    private static String getMethodServiceName(RpcService annotation, Method method) {
        return StringUtils.hasText(annotation.interfaceName())
                ? annotation.interfaceName()
                : method.getDeclaringClass().getName() + "#" + method.getName();
    }

    private static Class<?> getServiceInterface(RpcService annotation, Object bean) {
        return annotation.interfaceClass() != void.class
                ? annotation.interfaceClass()
                : bean.getClass().getInterfaces()[0];
    }

    public static ServiceMetadata buildConsumerMetadata(RpcReference reference, Class<?> type) {
        return ServiceMetadata.builder()
                .interfaceName(reference.interfaceName())
                .interfaceClass(type)
                .version(reference.version())
                .build();
    }
}
