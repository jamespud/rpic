package com.spud.rpic.io.serializer;

import com.spud.rpic.common.exception.SerializeException;

/**
 * @author Spud
 * @date 2025/2/9
 */
public interface Serializer {

	/**
	 * 序列化
	 */
	<T> byte[] serialize(T obj) throws SerializeException;

	/**
	 * 反序列化
	 */
	<T> T deserialize(byte[] data, Class<T> clz) throws SerializeException;

	/**
	 * 获取序列化类型
	 */
	String getType();

	/**
	 * 获取序列化类型编码
	 */
	default byte getCode() {
		return SerializerType.fromType(getType()).getCode();
	}
}