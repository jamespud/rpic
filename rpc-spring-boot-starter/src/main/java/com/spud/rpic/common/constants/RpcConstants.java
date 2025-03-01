package com.spud.rpic.common.constants;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class RpcConstants {
    public static final byte PROTOCOL_MAGIC_NUMBER = 0X35;
    public static final byte PROTOCOL_VERSION = 1;
    public static final byte TYPE_REQUEST = 0x0;
    public static final byte TYPE_RESPONSE = 0x1;
    public static final byte TYPE_HEARTBEAT = 0x2;
    public static final byte TYPE_ERROR = 0x3;
    // TODO: 其他常量定义
}
