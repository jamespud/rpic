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
public class OrcProtocolDecoder extends ByteToMessageDecoder {

    private static final int BASE_LENGTH = 7;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < BASE_LENGTH) {
            return;
        }

        in.markReaderIndex();
        byte magicNumber = in.readByte();
        if (magicNumber!= RpcConstants.MAGIC_NUMBER) {
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

        ProtocolMsg protocolMsg = new ProtocolMsg();
        protocolMsg.setMagicNumber(magicNumber);
        protocolMsg.setVersion(version);
        protocolMsg.setType(type);
        protocolMsg.setContentLength(contentLength);
        protocolMsg.setContent(content);

        out.add(protocolMsg);
    }
}