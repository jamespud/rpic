package com.spud.rpic.io.netty.server.invocation;

import com.spud.rpic.common.domain.RpcRequest;
import com.spud.rpic.common.domain.RpcResponse;

/**
 * 服务端调用接口
 * @author Spud
 * @date 2025/3/1
 */
public interface ServerInvocation {

  /**
   * 处理RPC请求
   */
  RpcResponse handleRequest(RpcRequest request);
}