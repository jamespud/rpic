package com.spud.rpic.io.netty;

import com.spud.rpic.common.constants.RpcConstants;
import com.spud.rpic.io.common.ProtocolMsg;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Slf4j
public class ProtocolEncoder extends MessageToByteEncoder<ProtocolMsg> {

  @Override
  protected void encode(ChannelHandlerContext ctx, ProtocolMsg msg, ByteBuf out) throws Exception {
    log.debug("Channel[{}] Encoding ProtocolMsg: type={} (hex: 0x{}), contentLength={}",
        ctx.channel().id().asShortText(), msg.getType(),
        Integer.toHexString(msg.getType() & 0xFF), msg.getContentLength());

    // 写入协议头部
    out.writeByte(msg.getMagicNumber());
    out.writeByte(msg.getVersion());
    out.writeByte(msg.getType());
    out.writeInt(msg.getContentLength());

    // 写入消息体
    if (msg.getContent() != null && msg.getContentLength() > 0) {
      out.writeBytes(msg.getContent());
    }

    log.debug("Channel[{}] Encoded ProtocolMsg, buffer size: {}, type: {} (hex: 0x{})",
        ctx.channel().id().asShortText(), out.readableBytes(), msg.getType(),
        Integer.toHexString(msg.getType() & 0xFF));
  }
}