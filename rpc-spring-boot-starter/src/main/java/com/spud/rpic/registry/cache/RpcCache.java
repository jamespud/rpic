package com.spud.rpic.registry.cache;

import java.util.function.Function;

/**
 * @author Spud
 * @date 2025/2/13
 */
public interface RpcCache<K, V> {
    V get(K key, Function<K, V> function);
    void put(K key, V value);

    void invalidate(K key);

    void invalidateAll();
}