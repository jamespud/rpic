package com.spud.rpic.registry.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Spud
 * @date 2025/2/13
 */
public class CaffeineCache<K, V> implements RpcCache<K, V> {
    private final LoadingCache<K, V> delegate;

    public CaffeineCache(Consumer<Caffeine<Object, Object>> configurator) {
        Caffeine<K, V> builder = Caffeine.newBuilder();
        configurator.accept(builder);
        this.delegate = builder.build(k -> null); // 禁用自动加载
    }

    @Override
    public V get(K key, Function<K, V> function) {
        return delegate.get(key) == null ? function.apply(key) : delegate.get(key);
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
}