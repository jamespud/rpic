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
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE}) 
public @interface RpcReference {
    Class<?> interfaceClass() default void.class; 

    String interfaceName() default ""; 

    String version() default ""; 

    String group() default ""; 

    String url() default ""; 

    String application() default ""; 

    String module() default ""; 

    String consumer() default ""; 

    String protocol() default ""; 

    String monitor() default ""; 

    String[] registry() default {}; 
    
    int timeout() default 5000;
}