package com.spud.rpic.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcReference {
  // 服务接口类型，可以省略
  Class<?> interfaceClass() default void.class;

  // 服务接口名称，默认为空字符串表示使用字段类型的全限定名
  String interfaceName() default "";

  // 服务版本号
  String version() default "1.0.0";

  // 服务分组
  String group() default "";

  // 调用超时时间（毫秒）
  int timeout() default 5000;

  // 是否检查服务存在
  boolean check() default true;

  // 重试次数
  int retries() default 2;

  // 负载均衡策略
  String loadbalance() default "random";

  // 集群容错策略
  String cluster() default "failover";
}