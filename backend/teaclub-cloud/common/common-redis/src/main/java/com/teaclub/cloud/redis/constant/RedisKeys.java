package com.teaclub.cloud.redis.constant;

import java.time.Duration;

/**
 * Redis 键命名规范与过期策略。
 *
 * <p>统一前缀 {@code tc:} 便于按业务域清理与监控。
 */
public final class RedisKeys {

    /** 全局键前缀 */
    public static final String PREFIX = "tc:";

    /** 登录态缓存 */
    public static final String LOGIN_SESSION = PREFIX + "login:session:";
    /** 登录失败计数 */
    public static final String LOGIN_FAIL_COUNT = PREFIX + "login:fail:";
    /** 图形验证码 */
    public static final String CAPTCHA = PREFIX + "captcha:";
    /** 短信验证码 */
    public static final String SMS_CODE = PREFIX + "sms:code:";

    /** 门店缓存 */
    public static final String STORE = PREFIX + "store:";
    /** 品牌缓存 */
    public static final String BRAND = PREFIX + "brand:";
    /** 会员缓存 */
    public static final String MEMBER = PREFIX + "member:";
    /** 热点商品缓存 */
    public static final String PRODUCT = PREFIX + "product:";
    /** 商品分类缓存 */
    public static final String PRODUCT_CATEGORY = PREFIX + "product:category:";
    /** 门店商品缓存 */
    public static final String STORE_PRODUCT = PREFIX + "store:product:";
    /** 钱包缓存 */
    public static final String WALLET = PREFIX + "wallet:";

    /** 分布式锁 */
    public static final String LOCK = PREFIX + "lock:";

    /** 幂等占位（消息消费） */
    public static final String IDEMPOTENT = PREFIX + "idempotent:";

    // ==================== 过期策略 ====================

    /** 登录态：2 小时 */
    public static final Duration TTL_LOGIN_SESSION = Duration.ofHours(2);
    /** 登录失败计数：15 分钟 */
    public static final Duration TTL_LOGIN_FAIL = Duration.ofMinutes(15);
    /** 图形验证码：5 分钟 */
    public static final Duration TTL_CAPTCHA = Duration.ofMinutes(5);
    /** 短信验证码：10 分钟 */
    public static final Duration TTL_SMS_CODE = Duration.ofMinutes(10);
    /** 门店缓存：30 分钟 */
    public static final Duration TTL_STORE = Duration.ofMinutes(30);
    /** 品牌缓存：1 小时 */
    public static final Duration TTL_BRAND = Duration.ofHours(1);
    /** 会员缓存：10 分钟 */
    public static final Duration TTL_MEMBER = Duration.ofMinutes(10);
    /** 商品缓存：15 分钟 */
    public static final Duration TTL_PRODUCT = Duration.ofMinutes(15);
    /** 分类缓存：1 小时 */
    public static final Duration TTL_PRODUCT_CATEGORY = Duration.ofHours(1);
    /** 门店商品缓存：10 分钟 */
    public static final Duration TTL_STORE_PRODUCT = Duration.ofMinutes(10);
    /** 钱包缓存：5 分钟（余额类数据过期要短，且钱包变动必须主动失效） */
    public static final Duration TTL_WALLET = Duration.ofMinutes(5);
    /** 消息幂等占位：24 小时 */
    public static final Duration TTL_IDEMPOTENT = Duration.ofHours(24);

    private RedisKeys() {
    }
}
