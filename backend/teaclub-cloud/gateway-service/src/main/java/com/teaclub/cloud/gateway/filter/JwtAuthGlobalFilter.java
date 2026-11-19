package com.teaclub.cloud.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.security.context.InternalHeaders;
import com.teaclub.cloud.security.jwt.JwtProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 网关 JWT 前置校验过滤器。
 *
 * <p>只做"身份是否有效"的判定；功能权限与数据范围由各微服务自行校验。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /**
     * 免认证路径白名单。
     *
     * <p><b>注意</b>：单体迁移后，管理端登录路径为 {@code /api/admin/auth/login}
     * （原 {@code /api/auth/login} 保留兼容），两者都必须放行，
     * 否则未登录用户无法获取令牌。
     */
    private static final List<String> WHITE_LIST = List.of(
            // 登录与注册
            "/api/auth/login",
            "/api/auth/register",
            "/api/admin/auth/login",
            "/api/admin/auth/register",
            // 支付回调（第三方服务器调用，无用户令牌）
            "/api/payment/notify/**",
            "/api/payment/refund-notify/**",
            "/api/admin/payment/notify/**",
            "/api/admin/payment/refund-notify/**",
            // 运维与文档
            "/actuator/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/doc.html"
    );

    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange, ResultCode.UNAUTHORIZED);
        }

        try {
            Claims claims = jwtProvider.parseAdminToken(auth.substring(7).trim());
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .header(InternalHeaders.USER_ID, String.valueOf(claims.getSubject()))
                    .header(InternalHeaders.USERNAME, str(claims.get("username")))
                    .header(InternalHeaders.BRAND_ID, str(claims.get("brandId")))
                    .header(InternalHeaders.ALL_STORE_ACCESS, str(claims.get("allStoreAccess")))
                    .header(InternalHeaders.ROLES, join(claims.get("roles")))
                    .header(InternalHeaders.PERMISSIONS, join(claims.get("permissions")))
                    .header(InternalHeaders.STORE_IDS, join(claims.get("storeIds")))
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (ExpiredJwtException e) {
            return unauthorized(exchange, ResultCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("网关 JWT 校验失败 path={} msg={}", path, e.getMessage());
            return unauthorized(exchange, ResultCode.TOKEN_INVALID);
        }
    }

    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(p -> MATCHER.match(p, path));
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String join(Object o) {
        if (o instanceof Collection<?> c) {
            return c.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
        return o == null ? "" : String.valueOf(o);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, ResultCode rc) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            String body = objectMapper.writeValueAsString(Result.fail(rc));
            DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return response.setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
