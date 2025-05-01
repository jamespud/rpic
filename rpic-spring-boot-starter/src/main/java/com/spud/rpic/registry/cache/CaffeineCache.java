package com.spud.rpic.registry.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Spud
 * @date 2025/2/13
 */
public class CaffeineCache<K, V> implements RpcCache<K, V> {

  private final Cache<K, V> delegate;

  public CaffeineCache(Consumer<Caffeine<K, V>> configurator) {
    Caffeine<K, V> builder = (Caffeine<K, V>) Caffeine.newBuilder()
        // 设置最大缓存条目数
        .maximumSize(1000)
        // 开启统计
        .recordStats();
    configurator.accept(builder);
    this.delegate = builder.build(k -> null);
  }

  public CacheStats getStats() {
    return delegate.stats();
  }

  @Override
  public V get(K key, Function<K, V> function) {
    try {
      V value = delegate.get(key, function);
      if (value == null) {
        delegate.invalidate(key);
      }
      return value;
    } catch (Exception e) {
      delegate.invalidate(key);
      return function.apply(key);
    }
  }

  @Override
  public void put(K key, V value) {
    delegate.put(key, value);
  }

  @Override
  public void invalidate(K key) {
    delegate.invalidate(key);
  }

  @Override
  public void invalidateAll() {
    delegate.invalidateAll();
  }

  public ConcurrentMap<K, V> asMap() {
    return delegate.asMap();
  }
}