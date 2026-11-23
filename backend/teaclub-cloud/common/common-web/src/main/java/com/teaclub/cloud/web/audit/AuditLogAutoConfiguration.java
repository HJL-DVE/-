package com.teaclub.cloud.web.audit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 审计日志自动装配。
 *
 * <p>为什么需要它：本类的 {@link AuditLogAspect} 位于 {@code com.teaclub.cloud.web} 包，
 * 而各服务的启动类在 {@code com.teaclub.cloud.<service>} 下，
 * Spring Boot 默认组件扫描**不会**扫到本包，导致切面从未注册。
 *
 * <p>这里用 {@code @Configuration} + 自动装配文件注册 Bean，无需各服务改启动类。
 */
@Configuration
@ConditionalOnProperty(name = "teaclub.audit.enabled", havingValue = "true", matchIfMissing = true)
public class AuditLogAutoConfiguration {

    @Bean
    public AuditLogAspect auditLogAspect(ObjectProvider<AuditLogWriter> writerProvider) {
        return new AuditLogAspect(writerProvider);
    }
}
