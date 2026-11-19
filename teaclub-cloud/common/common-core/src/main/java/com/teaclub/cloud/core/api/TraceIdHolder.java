package com.teaclub.cloud.core.api;

/**
 * 基于 ThreadLocal 的 traceId 持有器。
 *
 * <p>微服务场景下 traceId 需跨进程传递：
 * <ul>
 *   <li>入口（Gateway / 各服务 Web 层）从 Header {@code X-Trace-Id} 读取或新建</li>
 *   <li>Feign 调用时通过拦截器写入请求头</li>
 *   <li>MQ 消息头同样携带</li>
 * </ul>
 */
public final class TraceIdHolder {

    /** 跨进程传递 traceId 的请求头名称 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private TraceIdHolder() {
    }

    public static void set(String traceId) {
        HOLDER.set(traceId);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static boolean has() {
        return HOLDER.get() != null;
    }
}
