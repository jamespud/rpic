package com.spud.rpic.cluster;

import com.spud.rpic.model.ServiceURL;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Spud
 * @date 2025/2/27
 */
@Component
public class WeightedRoundRobinLoadBalancer implements LoadBalancer {

    private static class WeightedRoundRobin {
        private int weight;
        private final AtomicLong current = new AtomicLong(0);
        private long lastUpdate;

        public void setWeight(int weight) {
            this.weight = weight;
            current.set(0);
        }

        public long increaseCurrent() {
            return current.addAndGet(weight);
        }

        public void sel(int total) {
            current.addAndGet(-1 * total);
        }
    }

    // key: 服务URL的唯一标识
    private final Map<String, WeightedRoundRobin> weightMap = new ConcurrentHashMap<>();

    // 清理超时的权重记录的时间间隔（毫秒）
    private static final int RECYCLE_PERIOD = 60000;

    @Override
    public ServiceURL select(List<ServiceURL> urls) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }

        int totalWeight = 0;
        long maxCurrent = Long.MIN_VALUE;
        long now = System.currentTimeMillis();
        ServiceURL selectedUrl = null;
        WeightedRoundRobin selectedWRR = null;

        // 清理过期的权重记录
        List<String> expiredKeys = new ArrayList<>();
        for (Map.Entry<String, WeightedRoundRobin> entry : weightMap.entrySet()) {
            if (now - entry.getValue().lastUpdate > RECYCLE_PERIOD) {
                expiredKeys.add(entry.getKey());
            }
        }
        for (String key : expiredKeys) {
            weightMap.remove(key);
        }

        // 计算权重并选择服务
        for (ServiceURL url : urls) {
            String key = url.getAddress();
            int weight = url.getWeight();
            WeightedRoundRobin weightedRoundRobin = weightMap.computeIfAbsent(key, k -> {
                WeightedRoundRobin wrr = new WeightedRoundRobin();
                wrr.setWeight(weight);
                return wrr;
            });

            if (weight != weightedRoundRobin.weight) {
                weightedRoundRobin.setWeight(weight);
            }

            long cur = weightedRoundRobin.increaseCurrent();
            weightedRoundRobin.lastUpdate = now;
            if (cur > maxCurrent) {
                maxCurrent = cur;
                selectedUrl = url;
                selectedWRR = weightedRoundRobin;
            }
            totalWeight += weight;
        }

        if (selectedUrl != null) {
            selectedWRR.sel(totalWeight);
            return selectedUrl;
        }

        // 如果没有选中，使用普通轮询
        return urls.get(0);
    }

    @Override
    public String getType() {
        return LoadBalancerType.WEIGHTED_ROUND_ROBIN.getType();
    }
}
