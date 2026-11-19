package com.teaclub.cloud.core.api;

import lombok.Getter;

/**
 * 微服务统一错误码。
 *
 * <p>编码规则：前两位代表模块，后三位为序号。
 * 与单体项目（com.teaclub.common.api.ResultCode）保持一致的编码段，
 * 便于前端在迁移期统一处理。
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "ok"),

    // 通用 100xx
    PARAM_INVALID(10001, "参数校验失败"),
    BAD_REQUEST(10002, "请求参数不合法"),
    METHOD_NOT_ALLOWED(10003, "请求方法不支持"),
    NOT_FOUND(10004, "资源不存在"),
    SYSTEM_ERROR(50000, "系统内部错误"),

    // 认证 101xx
    UNAUTHORIZED(10101, "未登录或登录已失效"),
    TOKEN_EXPIRED(10102, "登录已过期，请重新登录"),
    TOKEN_INVALID(10103, "登录凭证无效"),
    BAD_CREDENTIALS(10104, "用户名或密码错误"),
    ACCOUNT_DISABLED(10105, "账号已被禁用"),

    // 授权 102xx
    FORBIDDEN(10201, "没有操作权限"),
    NO_STORE_PERMISSION(10202, "没有该门店的数据权限"),

    // 网关 103xx
    GATEWAY_NO_ROUTE(10301, "服务路由不存在"),
    GATEWAY_SERVICE_UNAVAILABLE(10302, "目标服务不可用"),
    GATEWAY_TIMEOUT(10303, "目标服务响应超时"),
    GATEWAY_RATE_LIMITED(10304, "请求过于频繁，请稍后再试"),

    // 内部调用 104xx
    /** 下游服务不可用 */
    DOWNSTREAM_UNAVAILABLE(10404, "下游服务暂不可用"),
    /** 下游服务超时 */
    DOWNSTREAM_TIMEOUT(10405, "下游服务响应超时"),
    /** 下游资源不存在 */
    DOWNSTREAM_NOT_FOUND(10406, "下游资源不存在"),
    /** Feign 调用失败 */
    FEIGN_CALL_FAILED(10401, "服务间调用失败"),
    INTERNAL_TOKEN_INVALID(10402, "内部调用凭证无效"),
    IDEMPOTENT_CONFLICT(10403, "重复请求，已忽略"),

    // 消息 105xx
    MESSAGE_SEND_FAILED(10501, "消息发送失败"),
    MESSAGE_CONSUME_FAILED(10502, "消息消费失败"),
    MESSAGE_DUPLICATE(10503, "消息重复消费，已忽略");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
