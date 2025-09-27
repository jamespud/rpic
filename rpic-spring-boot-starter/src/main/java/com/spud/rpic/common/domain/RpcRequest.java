package com.spud.rpic.common.domain;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Spud
 * @date 2025/2/9
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RpcRequest implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * 请求ID
	 */
	private String requestId;

	/**
	 * 接口名称
	 */
	private String interfaceName;

	/**
	 * 服务接口类
	 */
	private Class<?> interfaceClass;
	/**
	 * 方法名称
	 */
	private String methodName;

	private String serviceKey;

	/**
	 * 服务版本
	 */
	private String version;

	/**
	 * 服务分组
	 */
	private String group;

	/**
	 * 参数类型数组
	 */
	private Class<?>[] parameterTypes;

	/**
	 * 参数数组
	 */
	private Object[] parameters;

	/**
	 * 是否单向调用
	 */
	private boolean oneWay;

	/**
	 * 调用超时时间
	 */
	private long timeout;

	/**
	 * 全链路截止时间（毫秒时间戳），超过该时间请求应被丢弃
	 */
	private Long deadlineAtMillis;

	/**
	 * 当前尝试序号（首个请求为1），用于调试与观测
	 */
	private Integer attempt;

	/**
	 * 获取服务标识
	 */
	public String getServiceKey() {
		if (serviceKey != null) {
			return serviceKey;
		}
		StringBuilder buf = new StringBuilder();
		if (group != null && !group.isEmpty()) {
			buf.append(group).append("/");
		}
		buf.append(interfaceName);
		if (version != null && !version.isEmpty()) {
			buf.append(":").append(version);
		}
		serviceKey = buf.toString();
		return serviceKey;
	}

	/**
	 * 获取调用标识
	 */
	public String getInvokeKey() {
		return getServiceKey() + "#" + methodName;
	}

}