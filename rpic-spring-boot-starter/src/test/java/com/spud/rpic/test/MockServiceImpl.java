package com.spud.rpic.test;

import com.spud.rpic.annotation.RpcService;
import org.springframework.stereotype.Service;

/**
 * 测试用的服务实现
 */
@Service
@RpcService(version = "1.0.0")
public class MockServiceImpl implements MockService {

    @Override
    public String sayHello(String name) {
        return "Hello, " + name + "!";
    }

    @Override
    public int add(int a, int b) {
        return a + b;
    }

    @Override
    public void throwException() throws Exception {
        throw new RuntimeException("Test exception");
    }
}
