package com.spud.rpic.io.serializer;

import lombok.Getter;

/**
 * @author Spud
 * @date 2025/2/27
 */
@Getter
public enum SerializerType {
	JSON("JSON", (byte) 1),
	PROTOBUF("PROTOBUF", (byte) 2),
	HESSIAN("HESSIAN", (byte) 3),
	KRYO("KRYO", (byte) 4);

	private final String type;
	private final byte code;

	SerializerType(String type, byte code) {
		this.type = type;
		this.code = code;
	}

	public byte getCode() {
		return code;
	}

	public static SerializerType fromType(String type) {
		if (type == null) {
			return JSON;
		}
		String upper = type.toUpperCase();
		for (SerializerType serializerType : values()) {
			if (serializerType.type.equalsIgnoreCase(upper)) {
				return serializerType;
			}
		}
		throw new IllegalArgumentException("Unsupported serializer type: " + type);
	}

	public static SerializerType fromCode(byte code) {
		for (SerializerType serializerType : values()) {
			if (serializerType.code == code) {
				return serializerType;
			}
		}
		throw new IllegalArgumentException("Unsupported serializer code: " + code);
	}
}