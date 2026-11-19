package com.teaclub.common.api;

import lombok.Getter;

/**
 * 业务错误码。前两位代表模块，后三位为序号。
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "ok"),

    // 通用
    PARAM_INVALID(10001, "参数校验失败"),
    BAD_REQUEST(10002, "请求参数不合法"),
    METHOD_NOT_ALLOWED(10003, "请求方法不支持"),
    NOT_FOUND(10004, "资源不存在"),
    SYSTEM_ERROR(50000, "系统内部错误"),

    // 认证
    UNAUTHORIZED(10101, "未登录或登录已失效"),
    TOKEN_EXPIRED(10102, "登录已过期，请重新登录"),
    TOKEN_INVALID(10103, "登录凭证无效"),
    BAD_CREDENTIALS(10104, "用户名或密码错误"),
    ACCOUNT_DISABLED(10105, "账号已被禁用"),
    ACCOUNT_LOCKED(10106, "账号已被锁定，请稍后再试"),

    // 授权
    FORBIDDEN(10201, "没有操作权限"),
    NO_STORE_PERMISSION(10202, "没有该门店的数据权限"),

    // 系统管理
    USERNAME_EXISTS(20001, "登录账号已存在"),
    USER_NOT_FOUND(20002, "账号不存在"),
    ROLE_CODE_EXISTS(20003, "角色编码已存在"),
    PERMISSION_NOT_FOUND(20004, "权限点不存在"),
    ROLE_IN_USE(20005, "角色已被使用，无法删除"),
    BUILTIN_ROLE_IMMUTABLE(20006, "内置角色不允许修改或删除"),
    CANNOT_DELETE_SELF(20007, "不能删除当前登录账号"),

    // 门店 / 品牌
    STORE_NOT_FOUND(30001, "门店不存在"),
    STORE_CODE_EXISTS(30002, "门店编码已存在"),
    BRAND_NOT_FOUND(30003, "品牌不存在"),

    // 会员
    MEMBER_NOT_FOUND(40001, "会员不存在"),
    MEMBER_PHONE_EXISTS(40002, "手机号已注册"),
    LEVEL_NOT_FOUND(40003, "会员等级不存在"),
    LEVEL_CODE_EXISTS(40004, "等级编码已存在"),
    LEVEL_IN_USE(40005, "等级已被会员使用，无法删除"),

    // 商品
    CATEGORY_NOT_FOUND(50001, "商品分类不存在"),
    CATEGORY_HAS_CHILDREN(50002, "存在子分类，无法删除"),
    CATEGORY_HAS_PRODUCT(50003, "分类下存在商品，无法删除"),
    SPU_NOT_FOUND(50004, "商品不存在"),
    SPU_CODE_EXISTS(50005, "商品编码已存在"),
    SKU_CODE_EXISTS(50006, "规格编码已存在"),
    STORE_SKU_NOT_FOUND(50007, "门店商品不存在"),
    PRICE_BELOW_MIN(50008, "门店定价低于最低保护价"),
    PRICE_INVALID(50009, "价格必须大于 0");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
