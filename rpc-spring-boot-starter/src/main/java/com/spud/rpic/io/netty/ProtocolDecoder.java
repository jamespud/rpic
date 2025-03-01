package com.spud.rpic.io.netty;

import com.spud.rpic.common.constants.RpcConstants;
import com.spud.rpic.io.common.ProtocolMsg;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class ProtocolDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < ProtocolMsg.HEADER_LENGTH) {
            return;
        }

        in.markReaderIndex();
        byte magicNumber = in.readByte();
        if (magicNumber!= RpcConstants.PROTOCOL_MAGIC_NUMBER) {
            throw new IllegalArgumentException("Invalid magic number");
        }

        byte version = in.readByte();
        byte type = in.readByte();
        int contentLength = in.readInt();

        if (in.readableBytes() < contentLength) {
            in.resetReaderIndex();
            return;
        }

        byte[] content = new byte[contentLength];
        in.readBytes(content);

        ProtocolMsg protocolMsg = new ProtocolMsg(magicNumber, version, type, contentLength, content);
        out.add(protocolMsg);
    }
}