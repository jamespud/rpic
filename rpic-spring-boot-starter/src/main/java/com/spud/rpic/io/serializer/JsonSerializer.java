package com.spud.rpic.io.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spud.rpic.common.exception.SerializeException;
import java.io.IOException;

/**
 * @author Spud
 * @date 2025/2/18
 */
public class JsonSerializer implements Serializer {

	private final ObjectMapper objectMapper;

	public JsonSerializer() {
		objectMapper = new ObjectMapper();
		objectMapper.configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true);
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	@Override
	public <T> byte[] serialize(T obj) throws SerializeException {
		try {
			return objectMapper.writeValueAsBytes(obj);
		} catch (JsonProcessingException e) {
			throw new SerializeException("Error serializing object", e);
		}
	}

	@Override
	public <T> T deserialize(byte[] data, Class<T> clz) throws SerializeException {
		try {
			return objectMapper.readValue(data, clz);
		} catch (IOException e) {
			throw new SerializeException("Error deserializing object", e);
		}
	}

	@Override
	public String getType() {
		return SerializerType.JSON.getType();
	}
}
