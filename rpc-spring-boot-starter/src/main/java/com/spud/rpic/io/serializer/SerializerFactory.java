package com.spud.rpic.io.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Spud
 * @date 2025/2/27
 */
@Component
public class SerializerFactory {

    private final Map<Byte, Serializer> serializerMap = new HashMap<>();

    public SerializerFactory() {
        addSerializer(new JsonSerializer());
        addSerializer(new ProtobufSerializer());
        addSerializer(new HessianSerializer());
        addSerializer(new KryoSerializer());
    }

    public void addSerializer(Serializer serializer) {
        serializerMap.put(serializer.getType(), serializer);
    }

    public Serializer getSerializer(byte type) {
        Serializer serializer = serializerMap.get(type);
        if (serializer == null) {
            throw new IllegalArgumentException("Serializer not found for type: " + type);
        }
        return serializer;
    }
}