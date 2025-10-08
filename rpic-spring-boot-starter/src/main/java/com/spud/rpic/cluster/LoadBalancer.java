package com.spud.rpic.cluster;

import com.spud.rpic.model.ServiceURL;
import java.util.List;

/**
 * @author Spud
 * @date 2025/2/9
 */

public interface LoadBalancer {

	/**
	 * 从可用服务列表中选择一个
	 */
	ServiceURL select(List<ServiceURL> urls);

	default ServiceURL select(List<ServiceURL> urls, List<ServiceURL> triedInstances) {
		urls.removeAll(triedInstances);
		return select(urls);
	}

	/**
	 * 获取负载均衡器类型
	 */
	String getType();
}