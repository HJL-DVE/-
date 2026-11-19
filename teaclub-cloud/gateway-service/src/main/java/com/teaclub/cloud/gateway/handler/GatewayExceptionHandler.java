package com.teaclub.cloud.gateway.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.core.api.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * 网关全局异常处理：把路由不存在 / 服务不可用 / 超时统一转成 {@link Result}。
 */
@Slf4j
@Component
@Order(-1)
@RequiredArgsConstructor
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        ResultCode rc;
        HttpStatus status;

        if (ex instanceof TimeoutException) {
            rc = ResultCode.GATEWAY_TIMEOUT;
            status = HttpStatus.GATEWAY_TIMEOUT;
        } else if (ex instanceof ResponseStatusException rse && rse.getStatusCode().value() == 404) {
            rc = ResultCode.GATEWAY_NO_ROUTE;
            status = HttpStatus.NOT_FOUND;
        } else if (ex instanceof java.net.ConnectException
                || ex.getClass().getName().contains("NotFoundException")) {
            rc = ResultCode.GATEWAY_SERVICE_UNAVAILABLE;
            status = HttpStatus.SERVICE_UNAVAILABLE;
        } else {
            rc = ResultCode.SYSTEM_ERROR;
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        log.error("[网关异常] path={} type={} msg={}",
                exchange.getRequest().getPath().value(), ex.getClass().getSimpleName(), ex.getMessage());

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            String body = objectMapper.writeValueAsString(Result.fail(rc));
            DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return response.setComplete();
        }
    }
}
