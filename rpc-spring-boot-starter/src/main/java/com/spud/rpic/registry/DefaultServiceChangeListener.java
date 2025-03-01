package com.spud.rpic.registry;

import com.spud.rpic.model.ServiceURL;

import java.util.List;

/**
 * @author Spud
 * @date 2025/2/9
 */
// 服务变更监听器的具体实现类
public class DefaultServiceChangeListener implements ServiceChangeListener {
    @Override
    public void serviceChanged(String serviceName, List<ServiceURL> newServiceUrls) {
        // 这里可以添加具体的处理逻辑，例如更新本地服务缓存
        // TODO: 实现具体的服务变更逻辑
        System.out.println("Service " + serviceName + " has changed. New service URLs: " + newServiceUrls);
    }
}