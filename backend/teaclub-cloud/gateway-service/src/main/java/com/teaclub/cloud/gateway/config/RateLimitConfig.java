package com.teaclub.cloud.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * 限流键解析器：优先按用户 ID，其次按 IP。
 */
@Configuration
public class RateLimitConfig {

    @Bean
    @Primary
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }
            return Mono.just("ip:" + remoteIp(exchange));
        };
    }

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just("ip:" + remoteIp(exchange));
    }

    private static String remoteIp(org.springframework.web.server.ServerWebExchange exchange) {
        var addr = exchange.getRequest().getRemoteAddress();
        return addr == null ? "unknown" : addr.getAddress().getHostAddress();
    }
}
