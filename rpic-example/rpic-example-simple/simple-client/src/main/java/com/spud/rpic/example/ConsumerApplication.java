package com.spud.rpic.example;

import com.spud.rpic.annotation.RpcReference;
import com.spud.rpic.example.api.HelloService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author Spud
 * @date 2025/5/1
 */
@SpringBootApplication
@ComponentScan(basePackages = { "com.spud.rpic.example", "com.spud.rpic.config.bean" })
public class ConsumerApplication implements CommandLineRunner {

  @RpcReference(version = "1.0.0")
  private HelloService helloService;

  public static void main(String[] args) {
    SpringApplication.run(ConsumerApplication.class, args);
  }

  @Override
  public void run(String... args) {
    // 同步调用示例
    String result = helloService.sayHello("World");
    System.out.println("同步调用结果: " + result);

    // 异步调用示例
    helloService.sayHelloAsync("World")
        .thenAccept(r -> System.out.println("异步调用结果: " + r));
  }
}