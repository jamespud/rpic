package com.spud.rpic.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author Spud
 * @date 2025/5/1
 */
@SpringBootApplication
@ComponentScan(basePackages = { "com.spud.rpic.example", "com.spud.rpic.config.bean" })
public class ProviderApplication {

  public static void main(String[] args) {
    SpringApplication.run(ProviderApplication.class, args);
  }
}