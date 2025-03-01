package com.spud.rpic.io.serializer;

import lombok.Getter;

/**
 * @author Spud
 * @date 2025/2/27
 */
@Getter
public enum SerializerType {
    JSON((byte) 1),
    PROTOBUF((byte) 2),
    HESSIAN((byte) 3),
    KRYO((byte) 4);

    private final byte type;

    SerializerType(byte type) {
        this.type = type;
    }

    public static SerializerType valueOf(byte type) {
        for (SerializerType serializerType : values()) {
            if (serializerType.type == type) {
                return serializerType;
            }
        }
        throw new IllegalArgumentException("Unknown serializer type: " + type);
    }
}