package com.spud.rpic.common.exception;

/**
 * @author Spud
 * @date 2025/2/27
 */
public class SerializeException extends RpcException {

  private static final long serialVersionUID = 1L;

  public SerializeException(String message) {
    super(400, message);
  }

  public SerializeException(String message, Throwable cause) {
    super(400, message, cause);
  }
}