package com.teaclub.cloud.security.context;

/**
 * 微服务间传递上下文的标准请求头。
 *
 * <p>Gateway 解析 JWT 后写入这些头；Feign 拦截器负责继续向下游传递。
 */
public final class InternalHeaders {

    private InternalHeaders() {
    }

    public static final String TRACE_ID = "X-Trace-Id";
    public static final String USER_ID = "X-User-Id";
    public static final String USERNAME = "X-Username";
    public static final String BRAND_ID = "X-Brand-Id";
    public static final String USER_TYPE = "X-User-Type";
    public static final String ROLES = "X-Roles";

    /** 权限点集合（逗号分隔） */
    public static final String PERMISSIONS = "X-Permissions";
    public static final String ALL_STORE_ACCESS = "X-All-Store-Access";
    public static final String STORE_IDS = "X-Store-Ids";

    /** 会员端身份（小程序经 Gateway 解析会员令牌后写入） */
    public static final String MEMBER_ID = "X-Member-Id";

    /** 内部调用凭证，防止外部直接访问 /internal/** */
    public static final String INTERNAL_TOKEN = "X-Internal-Token";
}
