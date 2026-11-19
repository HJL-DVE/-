package com.teaclub.cloud.web.exception;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 微服务统一全局异常处理。
 *
 * <p>保证任何异常都以 {@link Result} 结构返回，且不向客户端泄露堆栈。
 */
@Slf4j
@org.springframework.web.bind.annotation.RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e, HttpServletRequest req) {
        log.warn("[业务异常] {} {} -> code={}, msg={}", req.getMethod(), req.getRequestURI(), e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .collect(Collectors.joining("; "));
        return Result.fail(ResultCode.PARAM_INVALID, msg);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .collect(Collectors.joining("; "));
        return Result.fail(ResultCode.PARAM_INVALID, msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(ResultCode.PARAM_INVALID, "缺少必要参数: " + e.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadable(HttpMessageNotReadableException e) {
        return Result.fail(ResultCode.BAD_REQUEST, "请求体格式错误");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethod(HttpRequestMethodNotSupportedException e) {
        return Result.fail(ResultCode.METHOD_NOT_ALLOWED, e.getMessage());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<Void> handleNotFound(NoHandlerFoundException e) {
        return Result.fail(ResultCode.NOT_FOUND, "接口不存在: " + e.getRequestURL());
    }

    /**
     * 权限不足（{@code @PreAuthorize} 拒绝）。
     *
     * <p>Spring Security 在方法级鉴权失败时抛出该异常，
     * 默认会被归到"系统异常"返回 500 —— 语义不对。
     * 权限不足应返回 403。
     */
    @ExceptionHandler({
            org.springframework.security.authorization.AuthorizationDeniedException.class,
            org.springframework.security.access.AccessDeniedException.class
    })
    public Result<Void> handleAccessDenied(Exception e) {
        return Result.fail(ResultCode.FORBIDDEN, "没有该操作权限");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e, HttpServletRequest req) {
        log.error("[系统异常] {} {}", req.getMethod(), req.getRequestURI(), e);
        return Result.fail(ResultCode.SYSTEM_ERROR);
    }

    private static String formatFieldError(FieldError fe) {
        return fe.getField() + ": " + fe.getDefaultMessage();
    }
}
