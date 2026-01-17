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

	/**
	 * 消息头长度：1(魔数) + 1(版本号) + 1(消息类型) + 1(序列化类型) + 4(内容长度)。
	 */
	public static final int HEADER_LENGTH = 1 + 1 + 1 + 1 + 4;

	/**
	 * 最大片段长度，默认 8MB。
	 */
	public static final int MAX_FRAME_LENGTH = 8 * 1024 * 1024;

	/**
	 * 默认超时时间，3秒。
	 */
	public static final int DEFAULT_TIMEOUT = 3000;

}
