package com.teaclub.cloud.feign.support;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Feign 异常解码器。
 *
 * <p>把下游服务的错误响应还原为统一的 {@code Result} / {@code BizException}，
 * 避免把下游的原始错误码直接抛给上层。
 */
@Slf4j
public class FeignCallExceptionHandler implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        String body = readBody(response);
        log.warn("Feign 调用失败: method={} status={} body={}", methodKey, response.status(), body);

        // 下游返回了业务错误结构
        Result<?> result = tryParse(body);
        if (result != null && result.getCode() != 0) {
            return new BizException(result.getCode(), result.getMessage());
        }

        // 按 HTTP 状态码映射
        return switch (response.status()) {
            case 400 -> new BizException(ResultCode.PARAM_INVALID);
            case 401 -> new BizException(ResultCode.UNAUTHORIZED);
            case 403 -> new BizException(ResultCode.FORBIDDEN);
            case 404 -> new BizException(ResultCode.DOWNSTREAM_NOT_FOUND);
            case 503 -> new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE);
            case 504 -> new BizException(ResultCode.DOWNSTREAM_TIMEOUT);
            default -> defaultDecoder.decode(methodKey, response);
        };
    }

    private String readBody(Response response) {
        if (response.body() == null) {
            return "";
        }
        try (InputStream is = response.body().asInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private Result<?> tryParse(String body) {
        if (body == null || body.isBlank() || !body.startsWith("{")) {
            return null;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(body, Result.class);
        } catch (Exception e) {
            return null;
        }
    }
}
