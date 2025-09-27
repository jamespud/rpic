package com.spud.rpic.io.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import lombok.extern.slf4j.Slf4j;

/**
 * 用于调试的日志处理器，记录所有通过网络传输的ByteBuf
 */
@Slf4j
public class LoggingChannelHandler extends ChannelDuplexHandler {

	private final String name;
	private final boolean printBytes;

	public LoggingChannelHandler(String name, boolean printBytes) {
		this.name = name;
		this.printBytes = printBytes;
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (msg instanceof ByteBuf) {
			ByteBuf buf = (ByteBuf) msg;
			log.info("[{}] RECEIVED: {} bytes", name, buf.readableBytes());
			if (printBytes && buf.readableBytes() > 0) {
				log.info("[{}] RECEIVED CONTENT: {}", name, byteBufToHexString(buf, 50));
			}
		} else {
			log.info("[{}] RECEIVED: {}", name, msg.getClass().getSimpleName());
		}
		super.channelRead(ctx, msg);
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		if (msg instanceof ByteBuf) {
			ByteBuf buf = (ByteBuf) msg;
			log.info("[{}] SENDING: {} bytes", name, buf.readableBytes());
			if (printBytes && buf.readableBytes() > 0) {
				log.info("[{}] SENDING CONTENT: {}", name, byteBufToHexString(buf, 50));
			}
		} else {
			log.info("[{}] SENDING: {}", name, msg.getClass().getSimpleName());
		}
		super.write(ctx, msg, promise);
	}

	/**
	 * 将ByteBuf转换为十六进制字符串
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