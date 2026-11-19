package com.teaclub.cloud.core.log;

import com.teaclub.cloud.core.api.TraceIdHolder;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * 日志上下文管理。
 *
 * <p>把 traceId、用户身份等写入 MDC，
 * 使日志格式 {@code %X{traceId}} 能输出链路信息。
 */
public final class TraceContext {

    private TraceContext() {
    }

    /**
     * 生成新的 traceId。
     */
    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 设置 traceId（同时写入 ThreadLocal 与 MDC）。
     */
    public static void setTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return;
        }
        TraceIdHolder.set(traceId);
        MDC.put(LogKeys.TRACE_ID, traceId);
    }

    /**
     * 获取当前 traceId。
     */
    public static String getTraceId() {
        String fromMdc = MDC.get(LogKeys.TRACE_ID);
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        return TraceIdHolder.get();
    }

    /**
     * 设置用户身份到 MDC。
     */
    public static void setUser(Long userId, String username) {
        if (userId != null) {
            MDC.put(LogKeys.USER_ID, String.valueOf(userId));
        }
        if (username != null && !username.isBlank()) {
            MDC.put(LogKeys.USERNAME, username);
        }
    }

    /**
     * 设置门店上下文到 MDC。
     */
    public static void setStore(Long storeId) {
        if (storeId != null) {
            MDC.put(LogKeys.STORE_ID, String.valueOf(storeId));
        }
    }

    /**
     * 设置品牌上下文到 MDC。
     */
    public static void setBrand(Long brandId) {
        if (brandId != null) {
            MDC.put(LogKeys.BRAND_ID, String.valueOf(brandId));
        }
    }

    /**
     * 清理全部 MDC 上下文（请求结束时调用，避免线程复用串号）。
     */
    public static void clear() {
        MDC.clear();
        TraceIdHolder.clear();
    }
}
