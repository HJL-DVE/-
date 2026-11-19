package com.teaclub.common.exception;

import com.teaclub.common.api.ResultCode;
import lombok.Getter;

/**
 * 业务异常。由 Service 层抛出，由 GlobalExceptionHandler 统一转换为响应。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ResultCode rc) {
        super(rc.getMessage());
        this.code = rc.getCode();
    }

    public BizException(ResultCode rc, String message) {
        super(message);
        this.code = rc.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException of(ResultCode rc) {
        return new BizException(rc);
    }
}
