package com.spud.rpic.io.serializer;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Spud
 * @date 2025/2/27
 */
public class SerializerFactory {

	private final Map<String, Serializer> serializerMap = new HashMap<>();
	private final Map<Byte, Serializer> serializerCodeMap = new HashMap<>();

	public SerializerFactory() {
		addSerializer(new JsonSerializer());
		addSerializer(new ProtobufSerializer());
		addSerializer(new HessianSerializer());
		addSerializer(new KryoSerializer());
	}

	public void addSerializer(Serializer serializer) {
		serializerMap.put(serializer.getType().toUpperCase(), serializer);
		serializerCodeMap.put(serializer.getCode(), serializer);
	}

	public Serializer getSerializer(String type) {
		if (type == null || type.isEmpty()) {
			throw new IllegalArgumentException("Type cannot be null or empty");
		}
		Serializer serializer = serializerMap.get(type.toUpperCase());
		if (serializer == null) {
			throw new IllegalArgumentException("Serializer not found for type: " + type);
		}
		return serializer;
	}

	public Serializer getSerializer(byte code) {
		Serializer serializer = serializerCodeMap.get(code);
		if (serializer == null) {
			throw new IllegalArgumentException("Serializer not found for code: " + code);
		}
		return serializer;
	}
}