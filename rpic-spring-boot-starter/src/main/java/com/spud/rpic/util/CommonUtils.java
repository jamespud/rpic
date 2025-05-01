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
    String interfaceName = StringUtils.hasText(annotation.interfaceName())
        ? annotation.interfaceName()
        : bean.getClass().getInterfaces()[0].getName();

    // 获取接口类
    Class<?> interfaceClass = annotation.interfaceClass() != void.class
        ? annotation.interfaceClass()
        : bean.getClass().getInterfaces()[0];

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
