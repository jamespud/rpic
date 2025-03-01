package com.spud.rpic.common.exception;

/**
 * @author Spud
 * @date 2025/2/27
 */
public class ServiceNotFoundException extends RpcException {
    private static final long serialVersionUID = 1L;

    public ServiceNotFoundException(String message) {
        super(404, message);
    }
}