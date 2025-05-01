package com.spud.rpic.io.serializer;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * @author Spud
 * @date 2025/2/27
 */
public class SerializerFactory {

  private final Map<String, Serializer> serializerMap = new HashMap<>();

  public SerializerFactory() {
    addSerializer(new JsonSerializer());
    addSerializer(new ProtobufSerializer());
    addSerializer(new HessianSerializer());
    addSerializer(new KryoSerializer());
  }

  public void addSerializer(Serializer serializer) {
    serializerMap.put(serializer.getType(), serializer);
  }

  public Serializer getSerializer(String type) {
    if (type == null || type.isEmpty()) {
      throw new IllegalArgumentException("Type cannot be null or empty");
    }
    type = type.toUpperCase();
    Serializer serializer = serializerMap.get(type);
    if (serializer == null) {
      throw new IllegalArgumentException("Serializer not found for type: " + type);
    }
    return serializer;
  }
}