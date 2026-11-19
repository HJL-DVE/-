package com.teaclub.cloud.web.filter;

import com.teaclub.cloud.core.api.TraceIdHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 微服务 TraceId 过滤器（Servlet 环境专用）。
 *
 * <p><b>不加 {@code @Component}</b>：本类继承 {@code OncePerRequestFilter}，
 * 属于 Servlet 栈。Gateway 是 WebFlux 应用，类路径没有 jakarta.servlet，
 * 若被组件扫描到会导致启动失败。
 *
 * <p>由 {@code TeaClubWebAutoConfiguration.ServletFilterConfig} 在存在
 * {@code jakarta.servlet.Filter} 时以 {@code @Bean} 方式注册。
 */
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = request.getHeader(TraceIdHolder.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        TraceIdHolder.set(traceId);
        MDC.put("traceId", traceId);
        response.setHeader(TraceIdHolder.TRACE_ID_HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
            TraceIdHolder.clear();
        }
    }
}
