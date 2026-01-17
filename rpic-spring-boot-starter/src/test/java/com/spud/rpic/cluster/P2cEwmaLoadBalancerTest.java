package com.spud.rpic.cluster;

import com.spud.rpic.model.ServiceURL;
import com.spud.rpic.property.RpcClientProperties;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 测试 P2cEwmaLoadBalancer 的功能。
 */
public class P2cEwmaLoadBalancerTest {

    private EndpointStatsRegistry endpointStatsRegistry;
    private P2cEwmaLoadBalancer loadBalancer;
    private RpcClientProperties clientProperties;

    @BeforeEach
    public void setUp() {
        clientProperties = new RpcClientProperties();
        endpointStatsRegistry = new EndpointStatsRegistry(clientProperties);
        loadBalancer = new P2cEwmaLoadBalancer(endpointStatsRegistry);
    }

    @Test
    public void testSelectReturnsNullWhenListIsEmpty() {
        Assertions.assertNull(loadBalancer.select(null));
        Assertions.assertNull(loadBalancer.select(Collections.emptyList()));
    }

    @Test
    public void testSelectReturnsSingleService() {
        ServiceURL url = createServiceURL("127.0.0.1:8080", 10);
        List<ServiceURL> urls = Collections.singletonList(url);
        Assertions.assertEquals(url, loadBalancer.select(urls));
    }

    @Test
    public void testSelectReturnsServiceWhenAllAreHealthy() {
        ServiceURL url1 = createServiceURL("127.0.0.1:8080", 10);
        ServiceURL url2 = createServiceURL("127.0.0.1:8081", 10);
        List<ServiceURL> urls = Arrays.asList(url1, url2);
        ServiceURL selected = loadBalancer.select(urls);
        Assertions.assertTrue(urls.contains(selected));
    }

    @Test
    public void testSelectPicksHealthyServicesFirst() {
        ServiceURL healthy = createServiceURL("127.0.0.1:8080", 10);
        ServiceURL unhealthy = createServiceURL("127.0.0.1:8081", 10);
        List<ServiceURL> urls = Arrays.asList(healthy, unhealthy);

        // 模拟不健康的节点被剔除
        RpcClientProperties.OutlierEjectionProperties outlierProps = clientProperties.getOutlier();
        outlierProps.setEnabled(true);
        outlierProps.setMinRequestVolume(1);

        for (int i = 0; i < 100; i++) {
            endpointStatsRegistry.onFailure(unhealthy.getAddress(), 1000, new RuntimeException("Test failure"));
        }

        ServiceURL selected = loadBalancer.select(urls);
        Assertions.assertEquals(healthy, selected);
    }

    @Test
    public void testSelectWithDifferentWeights() {
        ServiceURL url1 = createServiceURL("127.0.0.1:8080", 1);
        ServiceURL url2 = createServiceURL("127.0.0.1:8081", 100);
        List<ServiceURL> urls = Arrays.asList(url1, url2);

        // 没有统计数据时，应该根据权重选择（权重高的更容易被选中）
        int count1 = 0;
        int count2 = 0;
        int iterations = 1000;

        for (int i = 0; i < iterations; i++) {
            ServiceURL selected = loadBalancer.select(urls);
            if (selected.equals(url1)) {
                count1++;
            } else if (selected.equals(url2)) {
                count2++;
            }
        }

        Assertions.assertTrue(count2 > count1);
        System.out.printf("URL1 (weight=1) selected: %d times%n", count1);
        System.out.printf("URL2 (weight=100) selected: %d times%n", count2);
    }

    @Test
    public void testSelectWithLatencyStats() {
        ServiceURL url1 = createServiceURL("127.0.0.1:8080", 10);
        ServiceURL url2 = createServiceURL("127.0.0.1:8081", 10);
        List<ServiceURL> urls = Arrays.asList(url1, url2);

        // 模拟 url1 的延迟比 url2 低
        for (int i = 0; i < 100; i++) {
            endpointStatsRegistry.onSuccess(url1.getAddress(), 100);
            endpointStatsRegistry.onSuccess(url2.getAddress(), 1000);
        }

        // 多次选择，应该 url1 被选中的次数更多
        int count1 = 0;
        int count2 = 0;
        int iterations = 1000;

        for (int i = 0; i < iterations; i++) {
            ServiceURL selected = loadBalancer.select(urls);
            if (selected.equals(url1)) {
                count1++;
            } else if (selected.equals(url2)) {
                count2++;
            }
        }

        Assertions.assertTrue(count1 > count2);
        System.out.printf("URL1 (100ms) selected: %d times%n", count1);
        System.out.printf("URL2 (1000ms) selected: %d times%n", count2);
    }

    private ServiceURL createServiceURL(String address, int weight) {
        String[] parts = address.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        return new ServiceURL(host, port, "testInterface", "rpic", "testGroup", "1.0.0", weight, null);
    }
}
