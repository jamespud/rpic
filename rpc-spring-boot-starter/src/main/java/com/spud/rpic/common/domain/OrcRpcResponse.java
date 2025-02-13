package com.spud.rpic.common.domain;

import java.io.Serializable;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class OrcRpcResponse implements Serializable {
    private Object result;
    private Exception exception;
    private String requestId;

    // getters and setters
    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public Exception getException() {
        return exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}