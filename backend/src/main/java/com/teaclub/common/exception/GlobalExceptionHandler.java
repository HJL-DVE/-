package com.teaclub.common.exception;

import com.teaclub.common.api.Result;
import com.teaclub.common.api.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。保证任何异常都以统一 Result 结构返回，且不向客户端泄露堆栈。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e, HttpServletRequest req) {
        log.warn("[业务异常] {} {} -> code={}, msg={}", req.getMethod(), req.getRequestURI(), e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
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

    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException e, HttpServletRequest req) {
        log.warn("[越权访问] {} {} -> {}", req.getMethod(), req.getRequestURI(), e.getMessage());
        return Result.fail(ResultCode.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public Result<Void> handleAuth(AuthenticationException e) {
        return Result.fail(ResultCode.UNAUTHORIZED);
    }

    /**
     * 唯一索引冲突。数据层兜底，转成明确的业务错误而不是 500。
     * 常见场景：唯一编码已被软删除的历史数据占用。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e, HttpServletRequest req) {
        log.warn("[唯一约束冲突] {} {} -> {}", req.getMethod(), req.getRequestURI(), e.getMostSpecificCause().getMessage());
        return Result.fail(ResultCode.BAD_REQUEST,
                "数据已存在或编码与历史（含已删除）数据冲突，请更换编码后重试");
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