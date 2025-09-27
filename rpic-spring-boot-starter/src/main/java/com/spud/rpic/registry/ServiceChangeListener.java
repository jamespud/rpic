package com.spud.rpic.registry;

import com.spud.rpic.model.ServiceURL;

import java.util.List;

/**
 * @author Spud
 * @date 2025/2/9
 */
// 定义服务变更监听器接口
public interface ServiceChangeListener {

	// 当服务发生变更时调用该方法
	void serviceChanged(String serviceName, List<ServiceURL> newServiceUrls);
}