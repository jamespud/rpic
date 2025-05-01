package com.spud.rpic.io.netty;

import com.spud.rpic.common.domain.RpcRequest;
import com.spud.rpic.common.domain.RpcResponse;
import com.spud.rpic.model.ServiceURL;
import java.util.concurrent.CompletableFuture;

/**
 * 网络传输客户端接口
 * @author Spud
 * @date 2025/3/1
 */
public interface NetClient {

  /**
   * 发送RPC请求
   * @param serviceUrl 服务地址
   * @param request RPC请求
   * @param timeout 超时时间(毫秒)
   * @return RPC响应
   */
  RpcResponse send(ServiceURL serviceUrl, RpcRequest request, int timeout) throws Exception;

  /**
   * 异步发送RPC请求
   * @param serviceUrl 服务地址
   * @param request RPC请求
   * @param timeout 超时时间(毫秒)
   * @return CompletableFuture包装的RPC响应
   */
  CompletableFuture<RpcResponse> sendAsync(ServiceURL serviceUrl, RpcRequest request, int timeout);

  /**
   * 关闭客户端
   */
  void close();
}