package com.spud.rpic.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE}) 
@Inherited
public @interface RpcService {
    Class<?> interfaceClass() default void.class; 

    String interfaceName() default ""; 

    String version() default ""; 

    String group() default ""; 

    boolean export() default true;

    boolean register() default true;

    String application() default "";

    String module() default "";

    String provider() default "";

    String[] protocol() default {};

    String monitor() default "";

    String[] registry() default {};
    
    int weight() default 1;
}