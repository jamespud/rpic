package com.spud.rpic.io.netty;

import com.spud.rpic.io.common.ProtocolMsg;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class OrcProtocolEncoder extends MessageToByteEncoder<ProtocolMsg> {
    @Override
    protected void encode(ChannelHandlerContext ctx, ProtocolMsg msg, ByteBuf out) throws Exception {
        out.writeByte(msg.getMagicNumber());
        out.writeByte(msg.getVersion());
        out.writeByte(msg.getType());
        out.writeInt(msg.getContentLength());
        out.writeBytes(msg.getContent());
    }
}