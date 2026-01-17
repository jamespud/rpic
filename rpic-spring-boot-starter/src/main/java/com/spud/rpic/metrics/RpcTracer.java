package com.spud.rpic.metrics;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * OpenTelemetry 追踪工具类，用于 RPC 调用的分布式追踪
 */
@Slf4j
public final class RpcTracer {

    private static final String INSTRUMENTATION_NAME = "com.spud.rpic";
    private static final String INSTRUMENTATION_VERSION = "1.0.0";

    private final Tracer tracer;
    private final boolean enabled;

    private RpcTracer(Tracer tracer, boolean enabled) {
        this.tracer = tracer;
        this.enabled = enabled;
    }

    public static RpcTracer create() {
        try {
            OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
            Tracer tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
            return new RpcTracer(tracer, true);
        } catch (Exception e) {
            log.warn("OpenTelemetry is not available, tracing disabled: {}", e.getMessage());
            return new RpcTracer(null, false);
        }
    }

    /**
     * 开始客户端调用的追踪
     * @param service 服务名
     * @param method 方法名
     * @return 追踪上下文
     */
    public RpcTraceContext startClientSpan(String service, String method) {
        if (!enabled) {
            return RpcTraceContext.NOOP;
        }

        try {
            SpanBuilder spanBuilder = tracer.spanBuilder(method)
                .setAttribute("rpc.system", "rpic")
                .setAttribute("rpc.service", service)
                .setAttribute("rpc.method", method)
                .setAttribute("rpc.client.type", "netty");

            Span span = spanBuilder.startSpan();
            Context context = Context.current().with(span);
            Scope scope = context.makeCurrent();

            return new RpcTraceContext(span, context, scope);
        } catch (Exception e) {
            log.warn("Failed to start client span: {}", e.getMessage());
            return RpcTraceContext.NOOP;
        }
    }

    /**
     * 开始服务端调用的追踪
     * @param service 服务名
     * @param method 方法名
     * @return 追踪上下文
     */
    public RpcTraceContext startServerSpan(String service, String method) {
        if (!enabled) {
            return RpcTraceContext.NOOP;
        }

        try {
            SpanBuilder spanBuilder = tracer.spanBuilder(method)
                .setAttribute("rpc.system", "rpic")
                .setAttribute("rpc.service", service)
                .setAttribute("rpc.method", method)
                .setAttribute("rpc.server.type", "netty");

            Span span = spanBuilder.startSpan();
            Context context = Context.current().with(span);
            Scope scope = context.makeCurrent();

            return new RpcTraceContext(span, context, scope);
        } catch (Exception e) {
            log.warn("Failed to start server span: {}", e.getMessage());
            return RpcTraceContext.NOOP;
        }
    }

    /**
     * 追踪上下文类，用于管理追踪的生命周期
     */
    public static class RpcTraceContext {

        public static final RpcTraceContext NOOP = new RpcTraceContext(null, null, null);

        private final Span span;
        private final Context context;
        private final Scope scope;
        private boolean ended;

        private RpcTraceContext(Span span, Context context, Scope scope) {
            this.span = span;
            this.context = context;
            this.scope = scope;
            this.ended = false;
        }

        /**
         * 添加属性到追踪
         */
        public void setAttribute(String key, String value) {
            if (span != null && !ended) {
                span.setAttribute(key, value);
            }
        }

        /**
         * 添加属性到追踪
         */
        public void setAttribute(String key, long value) {
            if (span != null && !ended) {
                span.setAttribute(key, value);
            }
        }

        /**
         * 添加属性到追踪
         */
        public void setAttribute(String key, double value) {
            if (span != null && !ended) {
                span.setAttribute(key, value);
            }
        }

        /**
         * 添加属性到追踪
         */
        public void setAttribute(String key, boolean value) {
            if (span != null && !ended) {
                span.setAttribute(key, value);
            }
        }

        /**
         * 记录事件
         */
        public void addEvent(String name) {
            if (span != null && !ended) {
                span.addEvent(name);
            }
        }

        /**
         * 记录事件
         */
        public void addEvent(String name, Attributes attributes) {
            if (span != null && !ended) {
                span.addEvent(name, attributes);
            }
        }

        /**
         * 记录异常
         */
        public void recordException(Throwable throwable) {
            if (span != null && !ended) {
                span.recordException(throwable);
                span.setStatus(StatusCode.ERROR, throwable.getMessage());
            }
        }

        /**
         * 设置调用成功
         */
        public void setSuccess() {
            if (span != null && !ended) {
                span.setStatus(StatusCode.OK);
            }
        }

        /**
         * 设置调用失败
         */
        public void setFailure(String message) {
            if (span != null && !ended) {
                span.setStatus(StatusCode.ERROR, message);
            }
        }

        /**
         * 设置请求大小
         */
        public void setRequestSize(long bytes) {
            if (span != null && !ended) {
                span.setAttribute("rpc.request.size", bytes);
            }
        }

        /**
         * 设置响应大小
         */
        public void setResponseSize(long bytes) {
            if (span != null && !ended) {
                span.setAttribute("rpc.response.size", bytes);
            }
        }

        /**
         * 设置端点信息
         */
        public void setEndpoint(String endpoint) {
            if (span != null && !ended) {
                span.setAttribute("net.peer.name", parseHost(endpoint));
                span.setAttribute("net.peer.port", parsePort(endpoint));
            }
        }

        /**
         * 设置调用者信息
         */
        public void setCaller(String caller) {
            if (span != null && !ended) {
                span.setAttribute("net.peer.name", parseHost(caller));
                span.setAttribute("net.peer.port", parsePort(caller));
            }
        }

        /**
         * 设置重试信息
         */
        public void setRetry(boolean retried, int attempt) {
            if (span != null && !ended) {
                span.setAttribute("rpc.retried", retried);
                span.setAttribute("rpc.attempt", attempt);
            }
        }

        /**
         * 结束追踪
         */
        public void end() {
            if (ended || span == null) {
                return;
            }

            try {
                span.end();
            } finally {
                if (scope != null) {
                    scope.close();
                }
                ended = true;
            }
        }

        /**
         * 结束追踪
         */
        public void end(long duration, TimeUnit unit) {
            if (ended || span == null) {
                return;
            }

            try {
                span.end(duration, unit);
            } finally {
                if (scope != null) {
                    scope.close();
                }
                ended = true;
            }
        }

        private String parseHost(String endpoint) {
            if (endpoint == null || endpoint.isEmpty()) {
                return "unknown";
            }
            int colonIndex = endpoint.indexOf(':');
            if (colonIndex > 0) {
                return endpoint.substring(0, colonIndex);
            }
            return endpoint;
        }

        private int parsePort(String endpoint) {
            if (endpoint == null || endpoint.isEmpty()) {
                return -1;
            }
            int colonIndex = endpoint.indexOf(':');
            if (colonIndex > 0 && colonIndex < endpoint.length() - 1) {
                try {
                    return Integer.parseInt(endpoint.substring(colonIndex + 1));
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
            return -1;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
