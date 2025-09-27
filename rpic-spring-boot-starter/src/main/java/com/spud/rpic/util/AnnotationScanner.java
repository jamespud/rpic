package com.spud.rpic.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * @author Spud
 * @date 2025/2/26
 */
@Slf4j
public class AnnotationScanner {

	/**
	 * 扫描注解的统一接口
	 *
	 * @param context        Spring上下文
	 * @param annotationType 注解类型
	 * @param handler        注解处理器
	 */
	public static void scanAnnotations(ApplicationContext context,
	                                   Class<? extends Annotation> annotationType,
	                                   AnnotationHandler handler) {
		String[] beanNames = context.getBeanDefinitionNames();
		for (String beanName : beanNames) {
			Object bean = context.getBean(beanName);
			Class<?> beanClass = bean.getClass();

			// 扫描类级别注解
			if (beanClass.isAnnotationPresent(annotationType)) {
				Annotation annotation = beanClass.getAnnotation(annotationType);
				handler.handle(annotation, beanClass);
			}

			// 扫描方法级别注解
			for (Method method : beanClass.getDeclaredMethods()) {
				if (method.isAnnotationPresent(annotationType)) {
					Annotation annotation = method.getAnnotation(annotationType);
					handler.handle(annotation, method);
				}

				// 扫描方法参数注解
				for (Parameter parameter : method.getParameters()) {
					if (parameter.isAnnotationPresent(annotationType)) {
						Annotation annotation = parameter.getAnnotation(annotationType);
						handler.handle(annotation, parameter);
					}
				}
			}

			// 扫描字段级别注解
			ReflectionUtils.doWithFields(beanClass, field -> {
				if (field.isAnnotationPresent(annotationType)) {
					Annotation annotation = field.getAnnotation(annotationType);
					handler.handle(annotation, field);
				}
			});
		}
	}
}