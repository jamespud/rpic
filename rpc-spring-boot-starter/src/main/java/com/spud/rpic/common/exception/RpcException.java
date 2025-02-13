package com.spud.rpic.common.exception;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class RpcException extends RuntimeException {
    public RpcException(String message) {
        super(message);
    }

    public RpcException(String message, Throwable cause) {
        super(message, cause);
    }
}