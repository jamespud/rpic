package com.spud.rpic.test;

import com.spud.rpic.common.domain.RpcRequest;
import com.spud.rpic.common.domain.RpcResponse;
import com.spud.rpic.model.ServiceMetadata;
import com.spud.rpic.model.ServiceURL;
import java.util.UUID;

/**
 * 测试工具类，提供测试数据和通用方法
 */
public class TestUtils {

    public static final String TEST_SERVICE = "com.spud.rpic.test.TestService";
    public static final String TEST_METHOD = "testMethod";
    public static final String TEST_HOST = "localhost";
    public static final int TEST_PORT = 9000;

    /**
     * 创建测试用的 RpcRequest
     */
    public static RpcRequest createTestRequest() {
        RpcRequest request = new RpcRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setInterfaceClass(String.class);
        request.setMethodName(TEST_METHOD);
        request.setParameterTypes(new Class[]{String.class});
        request.setParameters(new Object[]{"test"});
        request.setServiceKey(TEST_SERVICE);
        return request;
    }

    /**
     * 创建测试用的 RpcResponse
     */
    public static RpcResponse createTestResponse(String requestId, Object result) {
        RpcResponse response = new RpcResponse();
        response.setRequestId(requestId);
        response.setResult(result);
        response.setError(false);
        return response;
    }

    /**
     * 创建测试用的 RpcResponse（错误）
     */
    public static RpcResponse createTestErrorResponse(String requestId, String errorMsg) {
        RpcResponse response = new RpcResponse();
        response.setRequestId(requestId);
        response.setError(true);
        response.setErrorMsg(errorMsg);
        return response;
    }

    /**
     * 创建测试用的 ServiceMetadata
     */
    public static ServiceMetadata createTestServiceMetadata() {
        return ServiceMetadata.builder()
            .interfaceName(TEST_SERVICE)
            .version("1.0.0")
            .group("default")
            .host(TEST_HOST)
            .port(TEST_PORT)
            .build();
    }

    /**
     * 创建测试用的 ServiceURL
     */
    public static ServiceURL createTestServiceURL() {
        return createTestServiceURL(TEST_HOST, TEST_PORT);
    }

    /**
     * 创建测试用的 ServiceURL
     */
    public static ServiceURL createTestServiceURL(String host, int port) {
        return new ServiceURL(host, port, TEST_SERVICE, "rpic", "default", "1.0.0", 1, null);
    }

    /**
     * 生成随机字符串
     */
    public static String randomString() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 睡眠指定时间（毫秒）
     */
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
