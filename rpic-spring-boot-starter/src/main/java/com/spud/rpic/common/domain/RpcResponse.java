package com.spud.rpic.common.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RpcResponse implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * 请求ID
	 */
	private String requestId;

	/**
	 * 返回结果
	 */
	private Object result;

	/**
	 * 是否有异常
	 */
	private Boolean error;

	/**
	 * 异常信息
	 */
	private String errorMsg;

	/**
	 * 异常类名
	 */
	private String errorClass;

	/**
	 * 处理时间（毫秒）
	 */
	private long processTime;

	/**
	 * 创建成功响应
	 */
	public static RpcResponse success(String requestId, Object result) {
		return RpcResponse.builder()
			.requestId(requestId)
			.result(result)
			.error(false)
			.build();
	}

	/**
	 * 创建异常响应
	 */
	public static RpcResponse error(String requestId, Throwable throwable) {
		return RpcResponse.builder()
			.requestId(requestId)
			.error(true)
			.errorMsg(throwable.getMessage())
			.errorClass(throwable.getClass().getName())
			.build();
	}

	/**
	 * 创建异常响应
	 */
	public static RpcResponse error(String requestId, String errorMsg) {
		return RpcResponse.builder()
			.requestId(requestId)
			.error(true)
			.errorMsg(errorMsg)
			.build();
	}

	/**
	 * 设置处理时间
	 */
	public void setProcessTime(long startTime) {
		this.processTime = System.currentTimeMillis() - startTime;
	}
}