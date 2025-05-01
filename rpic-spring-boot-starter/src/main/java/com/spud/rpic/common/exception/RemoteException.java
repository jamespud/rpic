package com.spud.rpic.common.exception;

/**
 * @author Spud
 * @date 2025/2/27
 */
public class RemoteException extends RpcException {

  private static final long serialVersionUID = 1L;

  public RemoteException(String message) {
    super(503, message);
  }

  public RemoteException(String message, Throwable cause) {
    super(503, message, cause);
  }
}