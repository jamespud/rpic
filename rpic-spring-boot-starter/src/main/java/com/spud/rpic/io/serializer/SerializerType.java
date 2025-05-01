package com.spud.rpic.io.serializer;

import lombok.Getter;

/**
 * @author Spud
 * @date 2025/2/27
 */
@Getter
public enum SerializerType {
  JSON( "JSON"),
  PROTOBUF("PROTOBUF"),
  HESSIAN("HESSIAN"),
  KRYO("KRYO");

  private final String type;

  SerializerType(String type) {
    this.type = type;
  }
}