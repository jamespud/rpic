package com.spud.rpic.io.common;

import com.spud.rpic.common.constants.RpcConstants;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Data
@AllArgsConstructor
@ToString
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
   * 消息头长度：1(魔数) + 1(版本号) + 1(消息类型) + 4(消息长度) = 7字节
   */
  public static final int HEADER_LENGTH = 7;

  /**
   * 最大帧长度
   */
  public static final int MAX_FRAME_LENGTH = 8 * 1024 * 1024;

  /**
   * 创建协议消息
   * 
   * @param bytes 消息内容
   * @param type  消息类型
   * @return 协议消息
   */
  public static ProtocolMsg fromBytes(byte[] bytes, byte type) {
    return new ProtocolMsg(RpcConstants.PROTOCOL_MAGIC_NUMBER, RpcConstants.PROTOCOL_VERSION,
        type, bytes.length, bytes);
  }

  /**
   * 创建请求消息
   * 
   * @param bytes 消息内容
   * @return 请求协议消息
   */
  public static ProtocolMsg fromBytes(byte[] bytes) {
    return fromBytes(bytes, RpcConstants.TYPE_REQUEST);
  }

  /**
   * 创建响应消息
   * 
   * @param bytes 消息内容
   * @return 响应协议消息
   */
  public static ProtocolMsg responseFromBytes(byte[] bytes) {
    return fromBytes(bytes, RpcConstants.TYPE_RESPONSE);
  }

  /**
   * 创建心跳消息
   * 
   * @return 心跳协议消息
   */
  public static ProtocolMsg heartBeat() {
    return new ProtocolMsg(RpcConstants.PROTOCOL_MAGIC_NUMBER, RpcConstants.PROTOCOL_VERSION,
        RpcConstants.TYPE_HEARTBEAT, 0, new byte[0]);
  }
}