package com.spud.rpic.io.netty.server.invocation;

import com.spud.rpic.common.domain.RpcRequest;
import com.spud.rpic.common.domain.RpcResponse;
import com.spud.rpic.property.RpcServerProperties;
import com.spud.rpic.test.MockService;
import com.spud.rpic.test.MockServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DefaultServerInvocation 测试类
 * 测试服务端请求处理逻辑
 */
@DisplayName("DefaultServerInvocation 测试")
class DefaultServerInvocationTest {

    private DefaultServerInvocation serverInvocation;
    private ApplicationContext mockApplicationContext;
    private MockService mockService;

    @BeforeEach
    void setUp() {
        // 创建配置属性
        RpcServerProperties serverProperties = new RpcServerProperties();
        serverProperties.setMaxConcurrentRequests(100);

        // 创建 ApplicationContext 模拟
        mockApplicationContext = mock(ApplicationContext.class);

        // 创建 MockService 实例
        mockService = new MockServiceImpl();

        // 创建服务器调用实例
        serverInvocation = new DefaultServerInvocation(serverProperties);
        serverInvocation.setApplicationContext(mockApplicationContext);
    }

    @Test
    @DisplayName("测试成功处理请求")
    void testHandleRequest_Success() {
        // 准备测试数据
        when(mockApplicationContext.getBean(MockService.class)).thenReturn(mockService);

        // 创建 RPC 请求
        RpcRequest request = createMockRequest(MockService.class, "sayHello", new Class[]{String.class}, "World");

        // 调用方法
        RpcResponse response = serverInvocation.handleRequest(request);

        // 验证结果
        assertNotNull(response);
        assertFalse(response.getError());
        assertEquals("Hello, World!", response.getResult());
        assertEquals(request.getRequestId(), response.getRequestId());
    }

    @Test
    @DisplayName("测试处理请求时抛出异常")
    void testHandleRequest_Exception() {
        // 准备测试数据
        when(mockApplicationContext.getBean(MockService.class)).thenReturn(mockService);

        // 创建 RPC 请求
        RpcRequest request = createMockRequest(MockService.class, "throwException", new Class[0]);

        // 调用方法
        RpcResponse response = serverInvocation.handleRequest(request);

        // 验证结果
        assertNotNull(response);
        assertTrue(response.getError());
        assertNotNull(response.getErrorMsg());
        assertEquals(request.getRequestId(), response.getRequestId());
    }

    @Test
    @DisplayName("测试服务端过载")
    void testHandleRequest_ServerOverload() throws InterruptedException {
        // 创建配置属性，设置最大并发请求数为1
        RpcServerProperties serverProperties = new RpcServerProperties();
        serverProperties.setMaxConcurrentRequests(1);

        // 创建可控的 semaphore 并注入到 DefaultServerInvocation 中，以避免使用反射
        java.util.concurrent.Semaphore testSemaphore = new java.util.concurrent.Semaphore(1);
        serverInvocation = new DefaultServerInvocation(serverProperties, testSemaphore);
        serverInvocation.setApplicationContext(mockApplicationContext);

        // 使用一个会阻塞的 MockService 实现来模拟长时间处理的请求
        MockService blockingService = new MockService() {
            @Override
            public String sayHello(String name) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "Hello, " + name + "!";
            }

            @Override
            public int add(int a, int b) {
                return mockService.add(a, b);
            }

            @Override
            public void throwException() throws Exception {
                mockService.throwException();
            }
        };

        when(mockApplicationContext.getBean(MockService.class)).thenReturn(blockingService);

        // 创建两个 RPC 请求
        RpcRequest request1 = createMockRequest(MockService.class, "sayHello", new Class[]{String.class}, "World1");
        RpcRequest request2 = createMockRequest(MockService.class, "sayHello", new Class[]{String.class}, "World2");

        // 在单独线程中发起第一个请求，使其在处理期间占用 semaphore
        Thread t = new Thread(() -> {
            RpcResponse resp = serverInvocation.handleRequest(request1);
            assertFalse(resp.getError());
        });
        t.start();

        // 确保第一个请求已开始并占用信号量
        Thread.sleep(50);

        // 此时第二个请求应被认为服务器过载
        RpcResponse response2 = serverInvocation.handleRequest(request2);
        assertTrue(response2.getError());
        assertTrue(response2.getErrorMsg().contains("Server is overloaded"));

        t.join();
    }

    @Test
    @DisplayName("测试无效请求")
    void testHandleRequest_InvalidRequest() {
        // 测试无效的方法名（方法不存在）
        RpcRequest invalidRequest = createMockRequest(MockService.class, "invalidMethod", new Class[0]);
        when(mockApplicationContext.getBean(MockService.class)).thenReturn(mockService);

        // 调用方法
        RpcResponse response = serverInvocation.handleRequest(invalidRequest);

        // 应该返回错误响应
        assertTrue(response.getError());
        assertTrue(response.getErrorMsg().contains("Method not found"));
    }

    @Test
    @DisplayName("测试服务未找到")
    void testHandleRequest_ServiceNotFound() {
        // 创建 RPC 请求
        RpcRequest request = createMockRequest(MockService.class, "sayHello", new Class[]{String.class}, "World");

        // 模拟找不到服务
        when(mockApplicationContext.getBean(MockService.class)).thenThrow(new RuntimeException("Service not found"));

        // 调用方法
        RpcResponse response = serverInvocation.handleRequest(request);

        // 验证结果
        assertNotNull(response);
        assertTrue(response.getError());
        assertTrue(response.getErrorMsg().contains("Service not found"));
    }

    @Test
    @DisplayName("测试不同方法调用")
    void testHandleRequest_DifferentMethods() {
        when(mockApplicationContext.getBean(MockService.class)).thenReturn(mockService);

        // 测试 sayHello 方法
        RpcRequest request1 = createMockRequest(MockService.class, "sayHello", new Class[]{String.class}, "World");
        RpcResponse response1 = serverInvocation.handleRequest(request1);
        assertFalse(response1.getError());
        assertEquals("Hello, World!", response1.getResult());

        // 测试 add 方法
        RpcRequest request2 = createMockRequest(MockService.class, "add", new Class[]{int.class, int.class}, 10, 20);
        RpcResponse response2 = serverInvocation.handleRequest(request2);
        assertFalse(response2.getError());
        assertEquals(30, response2.getResult());
    }

    @Test
    @DisplayName("测试并发请求处理")
    void testHandleRequest_Concurrent() throws InterruptedException {
        when(mockApplicationContext.getBean(MockService.class)).thenReturn(mockService);

        // 启动多个线程并发请求
        int threadCount = 5;
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                RpcRequest request = createMockRequest(MockService.class, "sayHello", new Class[]{String.class}, "Thread-" + threadId);
                RpcResponse response = serverInvocation.handleRequest(request);
                assertFalse(response.getError());
                assertTrue(((String) response.getResult()).contains("Thread-" + threadId));
            });
            threads[i].start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }
    }

    /**
     * 创建模拟的 RPC 请求
     */
    private RpcRequest createMockRequest(Class<?> interfaceClass, String methodName, Class<?>[] parameterTypes, Object... parameters) {
        RpcRequest request = new RpcRequest();
        request.setRequestId("test-request-" + System.currentTimeMillis());
        request.setInterfaceClass(interfaceClass);
        request.setMethodName(methodName);
        request.setParameterTypes(parameterTypes);
        request.setParameters(parameters);
        request.setServiceKey(String.format("%s:1.0.0:%s", interfaceClass.getName(), methodName));
        return request;
    }
}
