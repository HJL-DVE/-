package com.teaclub.common.api;

/**
 * 基于 ThreadLocal 的 traceId 持有器，贯穿单次请求。
 */
public final class TraceIdHolder {

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
}
