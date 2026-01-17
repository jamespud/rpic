package com.spud.rpic.io.common;

import com.spud.rpic.common.constants.RpcConstants;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

/**
 * 基础协议消息，包含头部和消息内容。
 */
@Data
@AllArgsConstructor
@ToString
public class ProtocolMsg {

	/**
	 * 魔数，用于快速校验。
	 */
	private byte magicNumber;

	/**
	 * 协议版本号。
	 */
	private byte version;

	/**
	 * 消息类型。
	 */
	private byte type;

	/**
	 * 序列化类型编码。
	 */
	private byte serializerType;

	/**
	 * 消息体长度。
	 */
	private int contentLength;

	/**
	 * 消息体内容。
	 */
	private byte[] content;

	/**
	 * 消息头长度：1(魔数) + 1(版本号) + 1(消息类型) + 1(序列化类型) + 4(内容长度)。
	 */

	public static ProtocolMsg fromBytes(byte[] bytes, byte type, byte serializerType) {
		return new ProtocolMsg(RpcConstants.PROTOCOL_MAGIC_NUMBER, RpcConstants.PROTOCOL_VERSION,
			type, serializerType, bytes.length, bytes);
	}

	public static ProtocolMsg fromBytes(byte[] bytes, byte serializerType) {
		return fromBytes(bytes, RpcConstants.TYPE_REQUEST, serializerType);
	}

	public static ProtocolMsg fromBytes(byte[] bytes) {
		return fromBytes(bytes, RpcConstants.DEFAULT_SERIALIZER);
	}

	public static ProtocolMsg responseFromBytes(byte[] bytes, byte serializerType) {
		return fromBytes(bytes, RpcConstants.TYPE_RESPONSE, serializerType);
	}

	public static ProtocolMsg responseFromBytes(byte[] bytes) {
		return responseFromBytes(bytes, RpcConstants.DEFAULT_SERIALIZER);
	}

	public static ProtocolMsg heartBeat() {
		return new ProtocolMsg(RpcConstants.PROTOCOL_MAGIC_NUMBER, RpcConstants.PROTOCOL_VERSION,
			RpcConstants.TYPE_HEARTBEAT, RpcConstants.DEFAULT_SERIALIZER, 0, new byte[0]);
	}
}