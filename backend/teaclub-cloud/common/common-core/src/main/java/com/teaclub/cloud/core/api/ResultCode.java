package com.teaclub.cloud.core.api;

/**
 * 统一错误码。
 *
 * <p>编号分段：1xxxx 通用/认证/网关/Feign/消息，2xxxx 用户/角色/权限，
 * 3xxxx 品牌/门店，4xxxx 会员，5xxxx 商品。
 *
 * <p>业务错误码由单体 backend 迁移而来，编号与消息保持一致，
 * 保证迁移后前端与调用方无需改动。
 */
public enum ResultCode {

    /** 成功 */
    SUCCESS(0, "ok"),
    // ==================== 通用 / 认证 / 网关 / Feign / 消息 ====================
    /** 参数校验失败 */
    PARAM_INVALID(10001, "参数校验失败"),
    /** 请求参数不合法 */
    BAD_REQUEST(10002, "请求参数不合法"),
    /** 请求方法不支持 */
    METHOD_NOT_ALLOWED(10003, "请求方法不支持"),
    /** 资源不存在 */
    NOT_FOUND(10004, "资源不存在"),
    /** 未登录或登录已失效 */
    UNAUTHORIZED(10101, "未登录或登录已失效"),
    /** 登录已过期，请重新登录 */
    TOKEN_EXPIRED(10102, "登录已过期，请重新登录"),
    /** 登录凭证无效 */
    TOKEN_INVALID(10103, "登录凭证无效"),
    /** 用户名或密码错误 */
    BAD_CREDENTIALS(10104, "用户名或密码错误"),
    /** 账号已被禁用 */
    ACCOUNT_DISABLED(10105, "账号已被禁用"),
    /** 账号已被锁定，请稍后再试 */
    ACCOUNT_LOCKED(10106, "账号已被锁定，请稍后再试"),
    /** 没有操作权限 */
    FORBIDDEN(10201, "没有操作权限"),
    /** 没有该门店的数据权限 */
    NO_STORE_PERMISSION(10202, "没有该门店的数据权限"),

    // ==================== 网关 / Feign / 消息 ====================
    /** 网关未找到对应路由 */
    GATEWAY_NO_ROUTE(10301, "网关未找到对应路由"),
    /** 目标服务暂不可用 */
    GATEWAY_SERVICE_UNAVAILABLE(10302, "目标服务暂不可用"),
    /** 网关请求超时 */
    GATEWAY_TIMEOUT(10303, "网关请求超时"),
    /** 请求过于频繁，已被限流 */
    GATEWAY_RATE_LIMITED(10304, "请求过于频繁，已被限流"),
    /** 服务间调用失败 */
    FEIGN_CALL_FAILED(10401, "服务间调用失败"),
    /** 内部调用凭证无效 */
    INTERNAL_TOKEN_INVALID(10402, "内部调用凭证无效"),
    /** 幂等冲突 */
    IDEMPOTENT_CONFLICT(10403, "幂等冲突"),
    /** 下游服务暂不可用 */
    DOWNSTREAM_UNAVAILABLE(10404, "下游服务暂不可用"),
    /** 下游服务响应超时 */
    DOWNSTREAM_TIMEOUT(10405, "下游服务响应超时"),
    /** 下游资源不存在 */
    DOWNSTREAM_NOT_FOUND(10406, "下游资源不存在"),
    /** 消息发送失败 */
    MESSAGE_SEND_FAILED(10501, "消息发送失败"),
    /** 消息消费失败 */
    MESSAGE_CONSUME_FAILED(10502, "消息消费失败"),
    /** 消息重复消费，已忽略 */
    MESSAGE_DUPLICATE(10503, "消息重复消费，已忽略"),
    // ==================== 用户 / 角色 / 权限 ====================
    /** 登录账号已存在 */
    USERNAME_EXISTS(20001, "登录账号已存在"),
    /** 账号不存在 */
    USER_NOT_FOUND(20002, "账号不存在"),
    /** 角色编码已存在 */
    ROLE_CODE_EXISTS(20003, "角色编码已存在"),
    /** 权限点不存在 */
    PERMISSION_NOT_FOUND(20004, "权限点不存在"),
    /** 角色已被使用，无法删除 */
    ROLE_IN_USE(20005, "角色已被使用，无法删除"),
    /** 内置角色不允许修改或删除 */
    BUILTIN_ROLE_IMMUTABLE(20006, "内置角色不允许修改或删除"),
    /** 不能删除当前登录账号 */
    CANNOT_DELETE_SELF(20007, "不能删除当前登录账号"),
    // ==================== 品牌 / 门店 ====================
    /** 门店不存在 */
    STORE_NOT_FOUND(30001, "门店不存在"),
    /** 门店编码已存在 */
    STORE_CODE_EXISTS(30002, "门店编码已存在"),
    /** 品牌不存在 */
    BRAND_NOT_FOUND(30003, "品牌不存在"),
    // ==================== 会员 ====================
    /** 会员不存在 */
    MEMBER_NOT_FOUND(40001, "会员不存在"),
    /** 手机号已注册 */
    MEMBER_PHONE_EXISTS(40002, "手机号已注册"),
    /** 会员等级不存在 */
    LEVEL_NOT_FOUND(40003, "会员等级不存在"),
    /** 等级编码已存在 */
    LEVEL_CODE_EXISTS(40004, "等级编码已存在"),
    /** 等级已被会员使用，无法删除 */
    LEVEL_IN_USE(40005, "等级已被会员使用，无法删除"),
    // ==================== 商品 ====================
    /** 系统内部错误 */
    SYSTEM_ERROR(50000, "系统内部错误"),
    /** 商品分类不存在 */
    CATEGORY_NOT_FOUND(50001, "商品分类不存在"),
    /** 存在子分类，无法删除 */
    CATEGORY_HAS_CHILDREN(50002, "存在子分类，无法删除"),
    /** 分类下存在商品，无法删除 */
    CATEGORY_HAS_PRODUCT(50003, "分类下存在商品，无法删除"),
    /** 商品不存在 */
    SPU_NOT_FOUND(50004, "商品不存在"),
    /** 商品编码已存在 */
    SPU_CODE_EXISTS(50005, "商品编码已存在"),
    /** 规格编码已存在 */
    SKU_CODE_EXISTS(50006, "规格编码已存在"),
    /** 门店商品不存在 */
    STORE_SKU_NOT_FOUND(50007, "门店商品不存在"),
    /** 门店定价低于最低保护价 */
    PRICE_BELOW_MIN(50008, "门店定价低于最低保护价"),
    /** 价格必须大于 0 */
    PRICE_INVALID(50009, "价格必须大于 0"),

    // ==================== 钱包 / 资金（6xxxx） ====================
    /** 余额不足 */
    WALLET_INSUFFICIENT(60001, "余额不足"),
    /** 钱包不存在 */
    WALLET_NOT_FOUND(60002, "钱包不存在"),
    /** 赠送金已被使用，自助退卡不可用，需转人工处理 */
    CARD_REFUND_GIFT_USED(60003, "赠送金额已被使用，需门店店长人工处理退卡"),
    /** 无实付余额可退 */
    CARD_REFUND_NO_PRINCIPAL(60004, "当前无实付本金可退"),
    /** 今日已办理过退卡 */
    CARD_REFUND_DAILY_LIMIT(60005, "今日已办理过退卡，请明日再试"),
    /** 退卡申请不存在 */
    CARD_REFUND_NOT_FOUND(60006, "退卡申请不存在"),
    /** 退卡申请已处理 */
    CARD_REFUND_ALREADY_HANDLED(60007, "该退卡申请已处理，请勿重复审核");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}