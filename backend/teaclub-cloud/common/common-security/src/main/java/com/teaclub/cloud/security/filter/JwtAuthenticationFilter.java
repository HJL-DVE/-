package com.teaclub.cloud.security.filter;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.security.context.InternalHeaders;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.UserContext;
import com.teaclub.cloud.security.jwt.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理端 JWT 认证过滤器（Servlet 环境专用）。
 *
 * <p><b>设计</b>：令牌只携带身份标识；角色、权限与门店范围由 Gateway 解析后
 * 通过请求头透传（{@code X-Roles} / {@code X-Store-Ids} / {@code X-All-Store-Access}），
 * 本过滤器据此构建 {@code Authentication}，供 {@code @PreAuthorize} 校验。
 *
 * <p><b>为什么不用 TokenPrincipalResolver 实时查库</b>：
 * auth-service 是唯一拥有 iam_db 的服务，其它服务不得跨库查询。
 * 因此权限上下文由 Gateway 统一解析并透传，各服务直接使用。
 *
 * <p>不加 {@code @Component}：继承 {@code OncePerRequestFilter} 属 Servlet 栈，
 * Gateway（WebFlux）扫描到会启动失败。由自动配置条件注册。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, ObjectMapper objectMapper) {
        this.jwtProvider = jwtProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            try {
                Claims claims = jwtProvider.parseAdminToken(token);
                Long userId = Long.valueOf(claims.getSubject());

                // 优先使用 Gateway 透传的权限上下文；直连时回落到令牌内的声明
                Set<String> roles = splitToSet(firstNonBlank(
                        request.getHeader(InternalHeaders.ROLES), claims.get("roles")));
                Set<String> permissions = splitToSet(firstNonBlank(
                        request.getHeader(InternalHeaders.PERMISSIONS), claims.get("permissions")));

                LoginUser user = LoginUser.builder()
                        .userId(userId)
                        .username(firstNonBlank(request.getHeader(InternalHeaders.USERNAME),
                                claims.get("username")))
                        .brandId(parseLong(firstNonBlank(request.getHeader(InternalHeaders.BRAND_ID),
                                claims.get("brandId"))))
                        .userType(request.getHeader(InternalHeaders.USER_TYPE))
                        .roles(roles)
                        .permissions(permissions)
                        .allStoreAccess(parseBoolean(
                                firstNonBlank(request.getHeader(InternalHeaders.ALL_STORE_ACCESS),
                                        claims.get("allStoreAccess"))))
                        .storeIds(splitToLongSet(request.getHeader(InternalHeaders.STORE_IDS)))
                        .jti(claims.getId())
                        .build();

                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                for (String p : permissions) {
                    authorities.add(new SimpleGrantedAuthority(p));
                }
                for (String r : roles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + r));
                }

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(user, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
                UserContext.set(user);
            } catch (ExpiredJwtException e) {
                writeError(response, ResultCode.TOKEN_EXPIRED);
                return;
            } catch (JwtException | IllegalArgumentException e) {
                writeError(response, ResultCode.TOKEN_INVALID);
                return;
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void writeError(HttpServletResponse response, ResultCode rc) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"code\":" + rc.getCode() + ",\"message\":\"" + rc.getMessage()
                        + "\",\"data\":null}");
    }

    private static String firstNonBlank(String a, Object b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b == null ? null : String.valueOf(b);
    }

    private static Set<String> splitToSet(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private static Set<Long> splitToLongSet(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toSet());
    }

    private static Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean parseBoolean(String value) {
        return value != null && "true".equalsIgnoreCase(value.trim());
    }
}
