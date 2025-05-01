package com.spud.rpic.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.stereotype.Component;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface RpcService {
  // 服务接口类型，默认为void.class表示使用实现类的第一个接口
  Class<?> interfaceClass() default void.class;

  // 服务接口名称，默认为空字符串表示使用接口全限定名
  String interfaceName() default "";

  // 服务版本号
  String version() default "1.0.0";

  // 服务分组
  String group() default "";

  // 服务权重
  int weight() default 1;

  // 服务超时时间（毫秒）
  int timeout() default 5000;

  // 是否注册到注册中心
  boolean register() default true;
}