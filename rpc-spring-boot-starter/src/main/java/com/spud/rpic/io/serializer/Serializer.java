package com.spud.rpic.io.serializer;

/**
 * @author Spud
 * @date 2025/2/9
 */
public interface Serializer {
    byte[] serialize(Object obj);
    <T> T deserialize(byte[] data, Class<T> clazz);
}