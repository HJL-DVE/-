package com.teaclub.cloud.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * 网关 Spring Security 配置。
 *
 * <p><b>为什么要显式配置</b>：
 * 引入 spring-boot-starter-security 后，Spring Boot 会自动启用默认安全策略
 * （弹出 Basic 认证、拦截所有请求返回 401/403、打印随机密码）。
 * 这会覆盖我们自己的 JWT 过滤器行为，导致：
 * <ul>
 *   <li>白名单接口被 403 拦截</li>
 *   <li>401 响应体为空（Spring 默认返回，不带业务 JSON）</li>
 * </ul>
 *
 * <p><b>设计说明</b>：网关的认证由 {@code JwtAuthGlobalFilter} 完成，
 * 这里把 Spring Security 的默认链路全部放行，避免双重拦截。
 * CSRF 在无状态 JWT 场景下无需开启。
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .logout(ServerHttpSecurity.LogoutSpec::disable)
            .authorizeExchange(exchange -> exchange.anyExchange().permitAll());
        return http.build();
    }
}
