package com.spud.rpic.common.constants;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Slf4j
public class RpcConstants {

  /**
   * 协议魔数
   */
  public static final byte PROTOCOL_MAGIC_NUMBER = 0X35;

  /**
   * 协议版本号
   */
  public static final byte PROTOCOL_VERSION = 1;

  /**
   * 请求消息类型 (1)
   * 必须与MessageType.REQUEST一致
   */
  public static final byte TYPE_REQUEST = 0x1;

  /**
   * 响应消息类型 (2)
   * 必须与MessageType.RESPONSE一致
   */
  public static final byte TYPE_RESPONSE = 0x2;

  /**
   * 心跳消息类型 (3)
   * 必须与MessageType.HEARTBEAT一致
   */
  public static final byte TYPE_HEARTBEAT = 0x3;

  /**
   * 错误消息类型 (4)
   * 必须与MessageType.HEARTBEAT_RESPONSE一致
   */
  public static final byte TYPE_ERROR = 0x4;

  // 静态初始化块，确保常量值正确加载
  static {
    log.info("RpcConstants loaded: MAGIC_NUMBER=0x{}, VERSION={}, TYPE_REQUEST=0x{}, TYPE_RESPONSE=0x{}",
        Integer.toHexString(PROTOCOL_MAGIC_NUMBER & 0xFF),
        PROTOCOL_VERSION,
        Integer.toHexString(TYPE_REQUEST & 0xFF),
        Integer.toHexString(TYPE_RESPONSE & 0xFF));

    // 验证常量值
    assert TYPE_REQUEST == 0x1 : "TYPE_REQUEST should be 0x1";
    assert TYPE_RESPONSE == 0x2 : "TYPE_RESPONSE should be 0x2";
    assert TYPE_HEARTBEAT == 0x3 : "TYPE_HEARTBEAT should be 0x3";
    assert TYPE_ERROR == 0x4 : "TYPE_ERROR should be 0x4";
  }
}
