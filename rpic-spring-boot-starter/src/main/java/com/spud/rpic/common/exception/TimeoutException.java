package com.spud.rpic.common.exception;

/**
 * @author Spud
 * @date 2025/2/27
 */
public class TimeoutException extends RpcException {

  private static final long serialVersionUID = 1L;

  public TimeoutException(String message) {
    super(408, message);
  }
}