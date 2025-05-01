package com.spud.rpic.io.netty.client.invocation;

import com.spud.rpic.common.domain.RpcRequest;
import com.spud.rpic.common.domain.RpcResponse;
import com.spud.rpic.model.ServiceMetadata;

/**
 * 客户端调用接口
 * @author Spud
 * @date 2025/3/1
 */
public interface ClientInvocation {

  /**
   * 执行远程调用
   */
  RpcResponse invoke(ServiceMetadata metadata, RpcRequest request, int timeout) throws Exception;
}