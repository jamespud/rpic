package com.spud.rpic.proxy;

import com.spud.rpic.annotation.RpcReference;

/**
 * @author Spud
 * @date 2025/2/16
 */
public interface ProxyFactory {

  /**
   * 创建服务代理
   *
   * @param interfaceType 接口类型
   * @param reference RPC引用注解
   * @return 代理对象
   */
  <T> T createProxy(Class<T> interfaceType, RpcReference reference);
}