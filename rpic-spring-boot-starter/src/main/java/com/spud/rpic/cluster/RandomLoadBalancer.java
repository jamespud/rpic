package com.spud.rpic.cluster;

import com.spud.rpic.model.ServiceURL;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Component;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class RandomLoadBalancer implements LoadBalancer {

  private final Random random = new Random();

  @Override
  public ServiceURL select(List<ServiceURL> urls) {
    if (urls == null || urls.isEmpty()) {
      return null;
    }
    return urls.get(random.nextInt(urls.size()));
  }

  @Override
  public String getType() {
    return LoadBalancerType.RANDOM.getType();
  }
}