package com.spud.rpic.io.protocol;

/**
 * @author Spud
 * @date 2025/2/9
 */
// 这里可以定义与协议相关的常量、工具方法等
public class OrcProtocol {

	public static final byte MAGIC_NUMBER = 0x35;
	public static final byte VERSION = 1;
	public static final int HEAD_LENGTH = 7; // magic(1) + version(1) + type(1) + length(4)
	public static final int MAX_FRAME_LENGTH = 8 * 1024 * 1024; // 8MB
	public static final int DEFAULT_TIMEOUT = 3000; // 3秒
}