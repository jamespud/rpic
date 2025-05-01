package com.spud.rpic.util;

import java.lang.annotation.Annotation;

/**
 * 注解处理器接口
 * @author Spud
 * @date 2025/2/26
 */
@FunctionalInterface
public interface AnnotationHandler {

  /**
   * 处理找到的注解
   * @param annotation 注解实例
   * @param element 被注解的元素(可能是Class、Method、Field或Parameter)
   */
  void handle(Annotation annotation, Object element);
}