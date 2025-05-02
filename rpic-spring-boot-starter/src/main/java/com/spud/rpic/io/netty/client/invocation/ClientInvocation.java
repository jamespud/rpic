package com.spud.rpic.io.netty.client.invocation;

import com.spud.rpic.common.domain.RpcRequest;
import com.spud.rpic.common.domain.RpcResponse;
import com.spud.rpic.model.ServiceMetadata;
import java.util.concurrent.CompletableFuture;

/**
 * 客户端调用接口
 * 
 * @author Spud
 * @date 2025/3/1
 */
public interface ClientInvocation {

  /**
   * 执行远程调用
   */
  RpcResponse invoke(ServiceMetadata metadata, RpcRequest request, int timeout) throws Exception;

  /**
   * 执行异步远程调用
   */
  CompletableFuture<RpcResponse> invokeAsync(ServiceMetadata metadata, RpcRequest request, int timeout);
}