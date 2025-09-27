package com.spud.rpic.common.exception;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class RpcException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final int code;

	public RpcException(String message) {
		super(message);
		this.code = 500;
	}

	public RpcException(int code, String message) {
		super(message);
		this.code = code;
	}

	public RpcException(String message, Throwable cause) {
		super(message, cause);
		this.code = 500;
	}

	public RpcException(int code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}

	public int getCode() {
		return code;
	}
}