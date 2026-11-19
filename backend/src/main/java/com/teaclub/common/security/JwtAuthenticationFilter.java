package com.teaclub.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teaclub.common.api.Result;
import com.teaclub.common.api.ResultCode;
import com.teaclub.common.context.LoginUser;
import com.teaclub.common.context.TokenPrincipalResolver;
import com.teaclub.common.context.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理端 JWT 认证过滤器。
 *
 * <p>令牌只携带身份标识；角色、权限与门店范围由 {@code AuthService} 在每次请求实时加载，
 * 因此后台修改权限或门店授权后可立即生效，无需等待令牌过期。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final TokenPrincipalResolver principalResolver;
    private final ObjectMapper objectMapper;

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
                LoginUser user = principalResolver.resolve(userId);
                if (user == null) {
                    writeError(response, ResultCode.ACCOUNT_DISABLED);
                    return;
                }
                user.setJti(claims.getId());

                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                for (String p : user.getPermissions()) {
                    authorities.add(new SimpleGrantedAuthority(p));
                }
                for (String r : user.getRoles()) {
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
                log.debug("JWT 校验失败: {}", e.getMessage());
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
        Result<Void> body = Result.fail(rc);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}