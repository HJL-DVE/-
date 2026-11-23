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
import java.util.Collection;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;

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
            Claims claims;
            boolean memberToken = false;
            try {
                claims = jwtProvider.parseAdminToken(token);
            } catch (ExpiredJwtException e) {
                // 管理端密钥下已过期：仍可能是会员令牌，继续尝试
                claims = parseMemberFallback(token);
                if (claims == null) {
                    writeError(response, ResultCode.TOKEN_EXPIRED);
                    return;
                }
                memberToken = true;
            } catch (JwtException | IllegalArgumentException e) {
                claims = parseMemberFallback(token);
                if (claims == null) {
                    writeError(response, ResultCode.TOKEN_INVALID);
                    return;
                }
                memberToken = true;
            }
            if (memberToken) {
                    Long memberId = parseLong(firstNonBlank(
                            request.getHeader(InternalHeaders.MEMBER_ID), claims.get("memberId")));
                    if (memberId == null) {
                        memberId = parseLong(claims.getSubject());
                    }
                    LoginUser member = LoginUser.builder()
                            .memberId(memberId)
                            .brandId(parseLong(firstNonBlank(
                                    request.getHeader(InternalHeaders.BRAND_ID), claims.get("brandId"))))
                            .userType("MEMBER")
                            .roles(Set.of("MEMBER"))
                            .permissions(Set.of())
                            .build();
                    UsernamePasswordAuthenticationToken memberAuth =
                            new UsernamePasswordAuthenticationToken(member, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
                    SecurityContextHolder.getContext().setAuthentication(memberAuth);
                    UserContext.set(member);
                } else {
                Long userId = Long.valueOf(claims.getSubject());

                // 权限/角色一律以令牌内的声明为准：token 经签名，是权威来源。
                // 不再优先使用 Gateway 透传的 X-Roles / X-Permissions 头——
                // 历史上这个头在跨服务转发时可能丢失或被覆盖，导致下游解析出空权限
                // （表现：登录时有权限，刷新后 /me 返回的 permissions 为空）。
                // 权限/角色双来源：
                // 1) 优先用 Gateway 透传的请求头（网关已解析过令牌，避免重复解析开销）；
                // 2) 请求头缺失时回退到令牌声明（令牌经签名，是权威来源）。
                // 这样既修复了「网关未转发原 Authorization 导致下游拿不到令牌」的问题，
                // 也兼容直连服务（无网关头）的场景。
                Set<String> roles = mergePermissionSource(
                        request.getHeader(InternalHeaders.ROLES), claims.get("roles"));
                Set<String> permissions = mergePermissionSource(
                        request.getHeader(InternalHeaders.PERMISSIONS), claims.get("permissions"));

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
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    /** 管理端解析失败（可能是会员令牌）时兜底解析；两种情况都失败返回 null。 */
    private Claims parseMemberFallback(String token) {
        try {
            return jwtProvider.parseMemberToken(token);
        } catch (ExpiredJwtException e) {
            return null;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
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

    /**
     * 把令牌声明转成字符串集合。
     *
     * <p>兼容两种形态：JWT 里通常是 List&lt;String&gt;，也可能是逗号分隔的字符串。
     */
    /**
     * 合并权限来源：请求头优先，缺失时用令牌声明。
     *
     * @param headerValue Gateway 透传的 X-Roles / X-Permissions 头（逗号分隔）
     * @param claimValue  令牌中的 roles / permissions 声明
     */
    private static Set<String> mergePermissionSource(String headerValue, Object claimValue) {
        Set<String> fromHeader = toStringSet(headerValue);
        if (!fromHeader.isEmpty()) {
            return fromHeader;
        }
        return toStringSet(claimValue);
    }

    private static Set<String> toStringSet(Object value) {
        if (value == null) {
            return Set.of();
        }
        // 集合：逐元素取值（jjwt 反序列化出的是 ArrayList，但不应依赖具体实现类）
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        }
        // 数组：可能是 Object[] 或原始类型数组
        if (value instanceof Object[] array) {
            return Arrays.stream(array)
                    .filter(java.util.Objects::nonNull)
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        }
        // 逗号分隔字符串
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return Set.of();
        }
        if (text.startsWith("[") && text.endsWith("]")) {
            // 形如 [a, b, c] 的字符串表示
            text = text.substring(1, text.length() - 1);
        }
        return Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private static Set<String> splitToSet(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(toSet());
    }

    private static Set<Long> splitToLongSet(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(toSet());
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
