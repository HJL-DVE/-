package com.teaclub.cloud.report;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 上下文加载测试：验证服务骨架可正常装配。
 *
 * <p>使用 mock 环境避免依赖真实 Nacos / MySQL。
 */
@SpringBootTest(
        classes = ReportApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.datasource.url=jdbc:h2:mem:test;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.flyway.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
        })
class ReportApplicationTests {

    @Test
    void contextLoads() {
        // 能加载上下文即代表公共组件（common-web / common-security / common-messaging）装配正常
    }
}
