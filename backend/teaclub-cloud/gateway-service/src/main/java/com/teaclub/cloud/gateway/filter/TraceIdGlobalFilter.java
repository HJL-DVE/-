package com.teaclub.cloud.gateway.filter;

import com.teaclub.cloud.core.api.TraceIdHolder;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 网关 TraceId 注入过滤器（最先执行）。
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceIdHolder.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        final String finalTraceId = traceId;

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(TraceIdHolder.TRACE_ID_HEADER, finalTraceId)
                .build();

        exchange.getResponse().getHeaders().add(TraceIdHolder.TRACE_ID_HEADER, finalTraceId);
        MDC.put("traceId", finalTraceId);
        TraceIdHolder.set(finalTraceId);

        return chain.filter(exchange.mutate().request(mutated).build())
                .doFinally(sig -> {
                    MDC.remove("traceId");
                    TraceIdHolder.clear();
                });
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
