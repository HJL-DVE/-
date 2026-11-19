package com.teaclub.cloud.security.config;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.security.context.InternalHeaders;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 业务服务统一安全链（Servlet 环境）。
 *
 * <p>认证方式：Gateway 解析 JWT 后通过请求头透传用户身份，
 * 本配置据此构建 SecurityContext。
 *
 * <p><b>重要</b>：这只是"身份识别"。
 * 具体功能权限（{@code @PreAuthorize}）与门店数据范围（{@code StoreScope}）
 * 仍由各服务在业务层自行校验。
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "jakarta.servlet.Filter")
@EnableMethodSecurity
@RequiredArgsConstructor
public class ResourceServerSecurityConfig {

    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain serviceFilterChain(HttpSecurity http,
                                               com.teaclub.cloud.security.filter.JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/api/*/health",
                        "/actuator/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/error"
                ).permitAll()
                .anyRequest().permitAll()   // 鉴权交由网关 + 方法级 @PreAuthorize 负责
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, resp, e) -> write(resp, ResultCode.UNAUTHORIZED))
                .accessDeniedHandler((req, resp, e) -> write(resp, ResultCode.FORBIDDEN)))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new GatewayContextFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void write(HttpServletResponse resp, ResultCode rc) throws IOException {
        resp.setStatus(rc == ResultCode.UNAUTHORIZED
                ? HttpServletResponse.SC_UNAUTHORIZED
                : HttpServletResponse.SC_FORBIDDEN);
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(objectMapper.writeValueAsString(Result.fail(rc)));
    }

    /**
     * 从网关透传的请求头构建登录用户上下文。
     *
     * <p>支持两种调用来源：
     * <ul>
     *   <li>经 Gateway：带 X-User-Id 等头</li>
     *   <li>服务间直连：可能不带，此时 UserContext 为空，由业务方法自行判断</li>
     * </ul>
     */
    static class GatewayContextFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            String userId = request.getHeader(InternalHeaders.USER_ID);
            if (userId != null && !userId.isBlank()) {
                LoginUser user = LoginUser.builder()
                        .userId(Long.valueOf(userId))
                        .username(request.getHeader(InternalHeaders.USERNAME))
                        .brandId(parseLong(request.getHeader(InternalHeaders.BRAND_ID)))
                        .userType(request.getHeader(InternalHeaders.USER_TYPE))
                        .roles(splitToSet(request.getHeader(InternalHeaders.ROLES)))
                        .allStoreAccess(Boolean.parseBoolean(request.getHeader(InternalHeaders.ALL_STORE_ACCESS)))
                        .storeIds(splitToLongSet(request.getHeader(InternalHeaders.STORE_IDS)))
                        .build();
                UserContext.set(user);
            }
            try {
                chain.doFilter(request, response);
            } finally {
                UserContext.clear();
            }
        }

        private static Long parseLong(String v) {
            try {
                return v == null || v.isBlank() ? null : Long.valueOf(v);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private static Set<String> splitToSet(String v) {
            if (v == null || v.isBlank()) {
                return Collections.emptySet();
            }
            return Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        }

        private static Set<Long> splitToLongSet(String v) {
            if (v == null || v.isBlank()) {
                return Collections.emptySet();
            }
            return Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                    .map(Long::valueOf).filter(Objects::nonNull).collect(Collectors.toSet());
        }
    }
}
