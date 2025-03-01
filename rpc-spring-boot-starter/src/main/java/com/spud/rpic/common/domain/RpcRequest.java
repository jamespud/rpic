package com.spud.rpic.common.domain;

import lombok.Builder;
import lombok.Data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * @author Spud
 * @date 2025/2/9
 */

@Data
@Builder
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

    private transient String serviceKey;

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
        serviceKey =  buf.toString();
        return serviceKey;
    }

    /**
     * 获取调用标识
     */
    public String getInvokeKey() {
        return getServiceKey() + "#" + methodName;
    }
    
}