package com.spud.rpic.test;

/**
 * 测试用的服务接口
 */
public interface MockService {

    String sayHello(String name);

    int add(int a, int b);

    void throwException() throws Exception;
}
