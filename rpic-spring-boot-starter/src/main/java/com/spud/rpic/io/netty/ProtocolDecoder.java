package com.spud.rpic.io.netty;

import com.spud.rpic.common.constants.RpcConstants;
import com.spud.rpic.io.common.ProtocolMsg;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Spud
 * @date 2025/2/9
 */

@Slf4j
public class ProtocolDecoder extends ByteToMessageDecoder {

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		// 打印接收到的原始数据的十六进制表示
		if (log.isDebugEnabled()) {
			log.debug("Channel[{}] Received raw bytes: {}",
				ctx.channel().id().asShortText(), byteBufToHexString(in, Math.min(in.readableBytes(), 50)));
		}

		// 确保有足够的字节可读
		if (in.readableBytes() < RpcConstants.HEADER_LENGTH) {
			log.debug("Channel[{}] Not enough bytes for header, waiting for more data. Available: {}",
				ctx.channel().id().asShortText(), in.readableBytes());
			return;
		}

		// 标记当前读索引，以便需要时重置
		in.markReaderIndex();

		// 记录开始解码的位置
		log.debug("Channel[{}] Starting to decode message at position: {}, readable bytes: {}",
			ctx.channel().id().asShortText(), in.readerIndex(), in.readableBytes());

		// 读取魔数
		byte magicNumber = in.readByte();
		if (magicNumber != RpcConstants.PROTOCOL_MAGIC_NUMBER) {
			in.resetReaderIndex();
			log.error("Channel[{}] Invalid magic number: {} (hex: 0x{}), expected: {} (hex: 0x{})",
				ctx.channel().id().asShortText(),
				magicNumber, Integer.toHexString(magicNumber & 0xFF),
				RpcConstants.PROTOCOL_MAGIC_NUMBER,
				Integer.toHexString(RpcConstants.PROTOCOL_MAGIC_NUMBER & 0xFF));

			// 尝试跳过这个错误的字节，寻找有效的魔数
			in.resetReaderIndex();
			skipInvalidBytes(ctx, in);
			return;
		}

		// 读取版本和类型
		byte version = in.readByte();
		byte type = in.readByte();
		byte serializerType = in.readByte();

		// 打印类型信息
		log.debug(
			"Channel[{}] Read message type: {} (hex: 0x{}), serializerType={}, comparing with TYPE_RESPONSE={}",
			ctx.channel().id().asShortText(), type, Integer.toHexString(type & 0xFF), serializerType,
			RpcConstants.TYPE_RESPONSE);

		// 读取内容长度
		int contentLength = in.readInt();

		// 确保长度合理
		if (contentLength < 0 || contentLength > RpcConstants.MAX_FRAME_LENGTH) {
			in.resetReaderIndex();
			log.error("Channel[{}] Invalid content length: {}", ctx.channel().id().asShortText(),
				contentLength);
			skipInvalidBytes(ctx, in);
			return;
		}

		// 如果可读字节不足，等待更多数据
		if (in.readableBytes() < contentLength) {
			in.resetReaderIndex();
			log.debug(
				"Channel[{}] Not enough bytes for content, waiting for more data. Need: {}, Available: {}",
				ctx.channel().id().asShortText(), contentLength, in.readableBytes());
			return;
		}

		// 读取内容
		byte[] content;
		if (contentLength > 0) {
			content = new byte[contentLength];
			in.readBytes(content);
			log.debug("Channel[{}] Read content bytes, length: {}, first few bytes: {}",
				ctx.channel().id().asShortText(), contentLength,
				bytesToHex(content, 0, Math.min(20, content.length)));
		} else {
			content = new byte[0];
			log.debug("Channel[{}] Read empty content (length=0)", ctx.channel().id().asShortText());
		}

		// 创建消息对象
		ProtocolMsg protocolMsg = new ProtocolMsg(magicNumber, version, type, serializerType,
			contentLength, content);
		log.debug(
			"Channel[{}] Decoded ProtocolMsg: magic=0x{}, version={}, type={} (hex: 0x{}), contentLength={}",
			ctx.channel().id().asShortText(),
			Integer.toHexString(magicNumber & 0xFF),
			version,
			type, Integer.toHexString(type & 0xFF),
			contentLength);

		// 验证解码的消息是否符合标准类型常量
		validateMessageType(ctx, protocolMsg);

		// 添加到输出列表
		log.debug("Channel[{}] Adding decoded message to output list: type={}",
			ctx.channel().id().asShortText(), type);
		out.add(protocolMsg);

		// 记录读取完成后的位置
		log.debug("Channel[{}] Finished decoding, reader index now at: {}, remaining bytes: {}",
			ctx.channel().id().asShortText(), in.readerIndex(), in.readableBytes());
	}

	/**
	 * 跳过无效字节，尝试寻找下一个有效的魔数
	 */
	private void skipInvalidBytes(ChannelHandlerContext ctx, ByteBuf in) {
		// 读取指针没有移动，而是尝试查找有效的魔数
		int skipped = 0;
		while (in.readableBytes() > 0) {
			if (in.getByte(in.readerIndex()) == RpcConstants.PROTOCOL_MAGIC_NUMBER) {
				log.debug("Channel[{}] Found potential valid magic number after skipping {} bytes",
					ctx.channel().id().asShortText(), skipped);
				return;
			}
			in.skipBytes(1);
			skipped++;
		}
		log.debug("Channel[{}] Skipped {} bytes, no valid magic number found",
			ctx.channel().id().asShortText(), skipped);
	}

	/**
	 * 验证消息类型是否符合标准常量
	 */
	private void validateMessageType(ChannelHandlerContext ctx, ProtocolMsg msg) {
		byte type = msg.getType();
		boolean isValid = type == RpcConstants.TYPE_REQUEST ||
			type == RpcConstants.TYPE_RESPONSE ||
			type == RpcConstants.TYPE_HEARTBEAT ||
			type == RpcConstants.TYPE_ERROR;

		if (!isValid) {
			log.warn("Channel[{}] Message type {} (hex: 0x{}) is not a standard type constant",
				ctx.channel().id().asShortText(), type, Integer.toHexString(type & 0xFF));
		} else {
			if (type == RpcConstants.TYPE_RESPONSE) {
				log.debug("Channel[{}] Valid RESPONSE message detected", ctx.channel().id().asShortText());
			} else if (type == RpcConstants.TYPE_REQUEST) {
				log.debug("Channel[{}] Valid REQUEST message detected", ctx.channel().id().asShortText());
			} else if (type == RpcConstants.TYPE_HEARTBEAT) {
				log.debug("Channel[{}] Valid HEARTBEAT message detected", ctx.channel().id().asShortText());
			}
		}
	}

	/**
	 * 将字节数组转换为十六进制字符串
	 */
	private String bytesToHex(byte[] bytes, int offset, int length) {
		StringBuilder sb = new StringBuilder();
		for (int i = offset; i < offset + length && i < bytes.length; i++) {
			sb.append(String.format("%02X ", bytes[i] & 0xFF));
		}
		return sb.toString();
	}

	/**
	 * 将ByteBuf转换为十六进制字符串用于调试
	 */
	private String byteBufToHexString(ByteBuf buf, int maxLength) {
		int readableBytes = Math.min(buf.readableBytes(), maxLength);
		if (readableBytes <= 0) {
			return "[]";
		}

		StringBuilder sb = new StringBuilder("[");
		int readerIndex = buf.readerIndex();

		for (int i = 0; i < readableBytes; i++) {
			byte b = buf.getByte(readerIndex + i);
			sb.append(String.format("%02X", b & 0xFF));
			if (i < readableBytes - 1) {
				sb.append(" ");
			}
		}

		if (readableBytes < buf.readableBytes()) {
			sb.append("...");
		}

		sb.append("]");
		return sb.toString();
	}
}