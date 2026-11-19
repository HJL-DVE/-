package com.teaclub.cloud.core.exception;

import com.teaclub.cloud.core.api.ResultCode;
import lombok.Getter;

/**
 * 微服务统一业务异常。
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
