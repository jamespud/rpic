package com.spud.rpic.example.impl;

import com.spud.rpic.annotation.RpcService;
import com.spud.rpic.example.api.HelloService;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/5/1
 */
@Service
@RpcService(version = "1.0.0", weight = 10)
public class HelloServiceImpl implements HelloService {

  @Override
  public String sayHello(String name) {
    return "Hello, " + name + "! This is from RPC server.";
  }

  @Override
  public CompletableFuture<String> sayHelloAsync(String name) {
    return CompletableFuture.supplyAsync(() -> sayHello(name));
  }
}