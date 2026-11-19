package com.teaclub.cloud.feign.config;

import com.teaclub.cloud.security.feign.FeignRequestInterceptor;
import feign.Feign;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 客户端自动配置。
 *
 * <p>注册上下文透传拦截器（TraceId / 用户身份 / 内部凭证）。
 */
@Configuration
@ConditionalOnClass(Feign.class)
public class TeaClubFeignClientAutoConfiguration {

    @Bean
    public FeignRequestInterceptor feignRequestInterceptor() {
        return new FeignRequestInterceptor();
    }
}
