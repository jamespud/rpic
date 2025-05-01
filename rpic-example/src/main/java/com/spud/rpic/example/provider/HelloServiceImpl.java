package com.spud.rpic.example.provider;

import com.spud.rpic.annotation.RpcService;
import com.spud.rpic.example.api.HelloService;
import java.util.concurrent.CompletableFuture;

/**
 * @author Spud
 * @date 2025/5/1
 */
@RpcService(interfaceClass = HelloService.class)
public class HelloServiceImpl implements HelloService {
  @Override
  public String sayHello(String name) {
    return "Hello, " + name;
  }

  @Override
  public CompletableFuture<String> sayHelloAsync(String name) {
    return CompletableFuture.supplyAsync(() -> sayHello(name));
  }
}