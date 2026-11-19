package com.teaclub.cloud.security.filter;

import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.security.context.InternalHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 内部接口保护过滤器（Servlet 环境专用）。
 *
 * <p>以 {@code /internal/} 开头的接口不通过 Gateway 暴露，
 * 且必须携带正确的 {@code X-Internal-Token}。
 *
 * <p><b>不加 {@code @Component}</b>：继承 {@code OncePerRequestFilter} 属 Servlet 栈，
 * Gateway（WebFlux）扫描到会启动失败。由
 * {@code TeaClubSecurityAutoConfiguration.ServletSecurityConfig} 条件注册。
 */
public class InternalTokenFilter extends OncePerRequestFilter {

    private final String internalToken;

    public InternalTokenFilter(String internalToken) {
        this.internalToken = internalToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/internal/")) {
            String token = request.getHeader(InternalHeaders.INTERNAL_TOKEN);
            if (token == null || !token.equals(internalToken)) {
                throw new BizException(ResultCode.INTERNAL_TOKEN_INVALID);
            }
        }
        chain.doFilter(request, response);
    }
}
