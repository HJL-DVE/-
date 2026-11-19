package com.teaclub.cloud.feign.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Feign 客户端超时与重试配置。
 */
@Data
@ConfigurationProperties(prefix = "teaclub.feign")
public class FeignClientProperties {

    /** 连接超时（毫秒） */
    private int connectTimeout = 3000;

    /** 读超时（毫秒） */
    private int readTimeout = 8000;

    /**
     * 默认最大重试次数。
     *
     * <p><b>默认 0</b>：Feign 的重试会重放请求，
     * 对有副作用的操作（扣款、支付创建、退款）是危险的。
     * 需要重试的只读接口应在客户端上单独声明 {@code @Retryable}。
     */
    private int defaultRetry = 0;

    /**
     * 允许重试的只读操作白名单（HTTP 方法 + 路径前缀）。
     *
     * <p>形如 {@code GET:/api/member/}。
     */
    private java.util.List<String> retryablePrefixes = new java.util.ArrayList<>();
}
