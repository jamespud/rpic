package com.spud.rpic.io.common;

import com.spud.rpic.common.constants.RpcConstants;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Data
@AllArgsConstructor
public class ProtocolMsg {
    /**
     * 魔数
     */
    private short magicNumber;

    /**
     * 版本号
     */
    private byte version;

    /**
     * 消息类型
     */
    private byte type;

    /**
     * 消息长度
     */
    private int contentLength;
    
    /**
     * 消息体
     */
    private byte[] content;

    /**
     * 消息头长度
     */
    public static final int HEADER_LENGTH = 8;

    /**
     * 最大帧长度
     */
    public static final int MAX_FRAME_LENGTH = 8 * 1024 * 1024;
    
    public static ProtocolMsg fromBytes(byte[] bytes) {
        return new ProtocolMsg(RpcConstants.PROTOCOL_MAGIC_NUMBER, RpcConstants.PROTOCOL_VERSION, RpcConstants.TYPE_REQUEST, HEADER_LENGTH, bytes);
    }
    
    public static ProtocolMsg heartBeat() {
        return new ProtocolMsg(RpcConstants.PROTOCOL_MAGIC_NUMBER, RpcConstants.PROTOCOL_VERSION, RpcConstants.TYPE_HEARTBEAT, HEADER_LENGTH, new byte[0]);
    }
}