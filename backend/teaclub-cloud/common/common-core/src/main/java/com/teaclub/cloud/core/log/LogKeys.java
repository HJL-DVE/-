package com.teaclub.cloud.core.log;

/**
 * 日志 MDC 键名常量。
 */
public final class LogKeys {

    /** 链路追踪 ID */
    public static final String TRACE_ID = "traceId";

    /** 用户 ID */
    public static final String USER_ID = "userId";

    /** 用户名 */
    public static final String USERNAME = "username";

    /** 品牌 ID */
    public static final String BRAND_ID = "brandId";

    /** 门店 ID */
    public static final String STORE_ID = "storeId";

    /** 服务名 */
    public static final String SERVICE = "service";

    /** 请求 URI */
    public static final String URI = "uri";

    private LogKeys() {
    }
}
