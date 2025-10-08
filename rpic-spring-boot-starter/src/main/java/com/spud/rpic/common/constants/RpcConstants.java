package com.spud.rpic.common.constants;

import com.spud.rpic.io.serializer.SerializerType;

/**
 * @author Spud
 * @date 2025/2/9
 */
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
	 * 请求消息类型 (1) 必须与MessageType.REQUEST一致
	 */
	public static final byte TYPE_REQUEST = 0x1;

	/**
	 * 响应消息类型 (2) 必须与MessageType.RESPONSE一致
	 */
	public static final byte TYPE_RESPONSE = 0x2;

	/**
	 * 心跳消息类型 (3) 必须与MessageType.HEARTBEAT一致
	 */
	public static final byte TYPE_HEARTBEAT = 0x3;

	/**
	 * 错误消息类型 (4) 必须与MessageType.HEARTBEAT_RESPONSE一致
	 */
	public static final byte TYPE_ERROR = 0x4;

	/**
	 * 默认序列化器编码
	 */
	public static final byte DEFAULT_SERIALIZER = SerializerType.KRYO.getCode();

}
