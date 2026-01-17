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
    void testHandleRequest_ServerOverload() {
        // 创建配置属性，设置最大并发请求数为1
        RpcServerProperties serverProperties = new RpcServerProperties();
        serverProperties.setMaxConcurrentRequests(1);
        serverInvocation = new DefaultServerInvocation(serverProperties);
        serverInvocation.setApplicationContext(mockApplicationContext);
        when(mockApplicationContext.getBean(MockService.class)).thenReturn(mockService);

        // 创建两个 RPC 请求
        RpcRequest request1 = createMockRequest(MockService.class, "sayHello", new Class[]{String.class}, "World1");
        RpcRequest request2 = createMockRequest(MockService.class, "sayHello", new Class[]{String.class}, "World2");

        // 第一个请求应该成功
        RpcResponse response1 = serverInvocation.handleRequest(request1);
        assertFalse(response1.getError());

        // 第二个请求应该由于服务器过载而失败
        // 由于 semaphore.release() 在 finally 块中，第一个请求完成后会释放信号量
        // 所以我们需要在第一个请求处理过程中发送第二个请求，模拟并发场景
        // 但由于测试是同步的，我们需要使用反射来直接操作 semaphore

        try {
            // 使用反射获取 semaphore 并强制 acquire，模拟服务器繁忙
            java.lang.reflect.Field semaphoreField = DefaultServerInvocation.class.getDeclaredField("semaphore");
            semaphoreField.setAccessible(true);
            java.util.concurrent.Semaphore semaphore = (java.util.concurrent.Semaphore) semaphoreField.get(serverInvocation);
            semaphore.acquire(); // 此时已使用完所有信号量

            RpcResponse response2 = serverInvocation.handleRequest(request2);
            assertTrue(response2.getError());
            assertTrue(response2.getErrorMsg().contains("Server is overloaded"));
        } catch (Exception e) {
            fail("测试过程中发生异常: " + e.getMessage());
        } finally {
            try {
                java.lang.reflect.Field semaphoreField = DefaultServerInvocation.class.getDeclaredField("semaphore");
                semaphoreField.setAccessible(true);
                java.util.concurrent.Semaphore semaphore = (java.util.concurrent.Semaphore) semaphoreField.get(serverInvocation);
                while (semaphore.availablePermits() < 1) {
                    semaphore.release();
                }
            } catch (Exception e) {
                // 忽略异常
            }
        }
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
