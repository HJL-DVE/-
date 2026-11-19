package com.teaclub.cloud.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * TeaClub 统一 API 网关。
 *
 * <p>职责边界：
 * <ul>
 *   <li>路由转发、JWT 前置校验、限流、跨域、访问日志、TraceId</li>
 *   <li><b>不承载业务逻辑</b>，不访问数据库</li>
 *   <li>网关校验只是第一道防线，各服务仍必须自行鉴权与数据范围校验</li>
 * </ul>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
