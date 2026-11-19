package com.teaclub.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 统一 API 响应包装。
 *
 * @param <T> 业务数据类型
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "统一响应结果")
public class Result<T> {

    @Schema(description = "业务状态码，0 表示成功")
    private int code;

    @Schema(description = "提示信息")
    private String message;

    @Schema(description = "业务数据")
    private T data;

    @Schema(description = "链路追踪 ID")
    private String traceId;

    @Schema(description = "服务端时间")
    private LocalDateTime timestamp;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
        this.traceId = TraceIdHolder.get();
    }

    public static <T> Result<T> ok() {
        return new Result<>(ResultCode.SUCCESS.getCode(), "ok", null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), "ok", data);
    }

    public static <T> Result<T> ok(T data, String message) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    public static <T> Result<T> fail(ResultCode rc) {
        return new Result<>(rc.getCode(), rc.getMessage(), null);
    }

    public static <T> Result<T> fail(ResultCode rc, String message) {
        return new Result<>(rc.getCode(), message, null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
