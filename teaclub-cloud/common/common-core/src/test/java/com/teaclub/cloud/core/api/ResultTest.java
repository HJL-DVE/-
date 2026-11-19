package com.teaclub.cloud.core.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统一响应结构测试。
 */
class ResultTest {

    @Test
    @DisplayName("成功响应 code=0")
    void okHasZeroCode() {
        Result<String> r = Result.ok("data");
        assertThat(r.getCode()).isZero();
        assertThat(r.getData()).isEqualTo("data");
        assertThat(r.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("无数据成功响应")
    void okWithoutData() {
        Result<Void> r = Result.ok();
        assertThat(r.getCode()).isZero();
        assertThat(r.getData()).isNull();
    }

    @Test
    @DisplayName("失败响应带错误码与消息")
    void failCarriesCode() {
        Result<Void> r = Result.fail(ResultCode.UNAUTHORIZED);
        assertThat(r.getCode()).isEqualTo(10101);
        assertThat(r.getMessage()).isEqualTo("未登录或登录已失效");
    }

    @Test
    @DisplayName("自定义错误码")
    void customCode() {
        Result<Void> r = Result.fail(99999, "custom");
        assertThat(r.getCode()).isEqualTo(99999);
        assertThat(r.getMessage()).isEqualTo("custom");
    }

    @Test
    @DisplayName("traceId 从 ThreadLocal 带入")
    void traceIdPropagated() {
        TraceIdHolder.set("abc123");
        try {
            assertThat(Result.ok().getTraceId()).isEqualTo("abc123");
        } finally {
            TraceIdHolder.clear();
        }
    }

    @Test
    @DisplayName("traceId 清理后不残留")
    void traceIdCleared() {
        TraceIdHolder.set("x");
        TraceIdHolder.clear();
        assertThat(TraceIdHolder.get()).isNull();
        assertThat(Result.ok().getTraceId()).isNull();
    }
}
