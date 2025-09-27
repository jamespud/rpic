package com.spud.rpic.example.api;

import java.util.concurrent.CompletableFuture;

/**
 * @author Spud
 * @date 2025/5/1
 */
public interface HelloService {

	String sayHello(String name);

	CompletableFuture<String> sayHelloAsync(String name);
}