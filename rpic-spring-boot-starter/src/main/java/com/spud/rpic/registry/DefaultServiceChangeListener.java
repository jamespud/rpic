package com.spud.rpic.registry;

import com.spud.rpic.model.ServiceURL;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Spud
 * @date 2025/2/9
 */
// 服务变更监听器的具体实现类
@Slf4j
public class DefaultServiceChangeListener implements ServiceChangeListener {

	private final Registry registry;

	public DefaultServiceChangeListener(Registry registry) {
		this.registry = registry;
	}

	public DefaultServiceChangeListener() {
		this.registry = null;
	}

	@Override
	public void serviceChanged(String serviceName, List<ServiceURL> newServiceUrls) {
		// Update registry discovery cache: invalidate entries matching the serviceName
		try {
			if (registry != null) {
				// discoveryCache keys are serviceId like interfaceName:version:group
				for (String key : registry.discoveryCache.asMap().keySet()) {
					if (key == null) continue;
					if (key.startsWith(serviceName + ":") || key.equals(serviceName)) {
						registry.discoveryCache.invalidate(key);
						// Optionally preload the new urls for this key if version/group can be inferred.
					}
				}
			}
			// Log the update for observability
			if (newServiceUrls != null) {
				log.info("Service " + serviceName + " changed, instances: " + newServiceUrls.size());
			} else {
				log.info("Service " + serviceName + " changed, no instances");
			}
		} catch (Exception e) {
			// Avoid throwing to listener thread; log instead
      log.error("Error handling service change for {}: {}", serviceName, e.getMessage());
		}
	}
}