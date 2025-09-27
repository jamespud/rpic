package com.spud.rpic.util;

import com.spud.rpic.common.exception.RemoteException;
import com.spud.rpic.common.exception.RpcException;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author Spud
 * @date 2025/2/27
 */
public class ExceptionUtils {

	/**
	 * 获取完整的异常堆栈信息
	 */
	public static String getStackTrace(Throwable throwable) throws IOException {

		try (StringWriter sw = new StringWriter();
		     PrintWriter pw = new PrintWriter(sw)) {
			throwable.printStackTrace(pw);
			return sw.toString();
		}
	}

	/**
	 * 获取根异常
	 */
	public static Throwable getRootCause(Throwable throwable) {
		Throwable cause = throwable.getCause();
		if (cause == null) {
			return throwable;
		}
		while (cause.getCause() != null) {
			cause = cause.getCause();
		}
		return cause;
	}

	/**
	 * 判断是否是业务异常
	 */
	public static boolean isBusinessException(Throwable throwable) {
		return throwable instanceof RpcException && !(throwable instanceof RemoteException);
	}

	/**
	 * 包装异常
	 */
	public static RpcException wrapException(Throwable throwable) {
		if (throwable instanceof RpcException) {
			return (RpcException) throwable;
		}
		return new RemoteException(throwable.getMessage(), throwable);
	}
}