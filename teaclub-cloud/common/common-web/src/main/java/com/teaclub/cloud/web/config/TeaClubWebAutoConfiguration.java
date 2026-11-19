package com.teaclub.cloud.web.config;

import com.teaclub.cloud.web.exception.GlobalExceptionHandler;
import com.teaclub.cloud.web.filter.TraceIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * common-web 自动装配入口。
 *
 * <p>关键设计：Servlet 相关组件（TraceId 过滤器、全局异常处理）
 * <b>只在 Servlet Web 应用中注册</b>。
 * Gateway 是 WebFlux（reactive）应用，没有 jakarta.servlet，
 * 若无条件注册会因类加载失败导致网关无法启动。
 *
 * <p>OpenAPI 配置与 Web 类型无关，单独导入。
 */
@AutoConfiguration
@Import(OpenApiConfig.class)
public class TeaClubWebAutoConfiguration {

    /**
     * Servlet 型业务服务的通用组件。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "jakarta.servlet.Filter")
    static class ServletComponents {

        @Bean
        public TraceIdFilter traceIdFilter() {
            return new TraceIdFilter();
        }

        @Bean
        public GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }
    }
}
