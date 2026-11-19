package com.teaclub.cloud.feign.config;

import com.teaclub.cloud.feign.support.FeignCallExceptionHandler;
import com.teaclub.cloud.feign.support.FeignRetryPolicy;
import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Feign 统一配置：超时、重试、异常解码、日志级别。
 */
@Configuration
@ConditionalOnClass(feign.Feign.class)
@EnableConfigurationProperties(FeignClientProperties.class)
@RequiredArgsConstructor
public class TeaClubFeignAutoConfiguration {

    private final FeignClientProperties properties;

    /**
     * 请求超时配置。
     */
    @Bean
    public Request.Options feignRequestOptions() {
        return new Request.Options(
                properties.getConnectTimeout(), TimeUnit.MILLISECONDS,
                properties.getReadTimeout(), TimeUnit.MILLISECONDS,
                true);
    }

    /**
     * 重试策略。
     *
     * <p>默认 {@link Retryer#NEVER_RETRY}。
     * 只读接口可通过 {@link FeignRetryPolicy} 显式开启。
     */
    @Bean
    public Retryer feignRetryer() {
        if (properties.getDefaultRetry() <= 0) {
            return Retryer.NEVER_RETRY;
        }
        return new FeignRetryPolicy(properties.getDefaultRetry(),
                properties.getConnectTimeout() * 2L, properties.getReadTimeout());
    }

    /**
     * 异常解码：把下游错误转成统一的 {@code Result}。
     */
    @Bean
    @ConditionalOnClass(ErrorDecoder.class)
    public ErrorDecoder feignErrorDecoder() {
        return new FeignCallExceptionHandler();
    }

    /**
     * Feign 日志级别。
     *
     * <p>默认 BASIC，避免 FULL 打印请求体（含敏感信息）。
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
