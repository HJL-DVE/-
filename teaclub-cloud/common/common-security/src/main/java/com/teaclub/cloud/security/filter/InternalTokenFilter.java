package com.teaclub.cloud.security.filter;

import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.security.context.InternalHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
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
        // 内部接口路径形如 /api/{service}/internal/**（不是根路径 /internal/）
        if (isInternalPath(path)) {
            String token = request.getHeader(InternalHeaders.INTERNAL_TOKEN);
            if (token == null || !token.equals(internalToken)) {
                // 过滤器中直接写响应：抛 BizException 会被 Spring 当作 500，
                // 而凭证无效语义上应是 401。
                writeUnauthorized(response);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * 判断是否为内部接口路径。
     *
     * <p>识别规则：路径中包含 {@code /internal/} 段。
     * 例：{@code /api/member/internal/user/1}、{@code /internal/user/1}
     */
    /**
     * 输出 401 响应（统一 Result 结构）。
     */
    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"code\":" + ResultCode.INTERNAL_TOKEN_INVALID.getCode()
                        + ",\"message\":\"" + ResultCode.INTERNAL_TOKEN_INVALID.getMessage()
                        + "\",\"data\":null}");
    }

    private boolean isInternalPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        // "/internal/" 自带前导斜杠，本身即一个完整路径段；
        // 例如 /api/member/internal/user/1 中，前一段是 "member"。
        if (path.contains("/internal/")) {
            return true;
        }
        // 路径恰好以 /internal 结尾
        return path.endsWith("/internal");
    }
}
