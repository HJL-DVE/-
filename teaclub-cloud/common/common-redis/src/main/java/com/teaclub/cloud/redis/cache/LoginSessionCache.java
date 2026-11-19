package com.teaclub.cloud.redis.cache;

import com.teaclub.cloud.redis.constant.RedisKeys;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.Optional;

/**
 * 登录态缓存。
 *
 * <p>存储用户登录后的会话信息，键为 token 摘要。
 * 退出登录时删除该键。
 */
@RequiredArgsConstructor
public class LoginSessionCache {

    private final CacheService cacheService;

    /**
     * 保存登录会话。
     *
     * @param token     访问令牌
     * @param sessionId 会话标识
     * @param userId    用户 ID
     */
    public void save(String token, String sessionId, Long userId) {
        cacheService.set(RedisKeys.LOGIN_SESSION + digest(token),
                sessionId + ":" + userId, RedisKeys.TTL_LOGIN_SESSION);
    }

    /**
     * 读取登录会话。
     */
    public Optional<String> find(String token) {
        Object v = cacheService.get(RedisKeys.LOGIN_SESSION + digest(token));
        return Optional.ofNullable(v).map(String::valueOf);
    }

    /** 删除登录会话（退出登录） */
    public void remove(String token) {
        cacheService.delete(RedisKeys.LOGIN_SESSION + digest(token));
    }

    /** 延长会话有效期 */
    public void touch(String token) {
        String key = RedisKeys.LOGIN_SESSION + digest(token);
        if (cacheService.hasKey(key)) {
            cacheService.expire(key, RedisKeys.TTL_LOGIN_SESSION);
        }
    }

    /**
     * 登录失败计数。达到上限应拒绝登录。
     */
    public long recordLoginFailure(String identifier) {
        return cacheService.increment(RedisKeys.LOGIN_FAIL_COUNT + identifier, RedisKeys.TTL_LOGIN_FAIL);
    }

    /** 登录成功后清除计数 */
    public void clearLoginFailure(String identifier) {
        cacheService.delete(RedisKeys.LOGIN_FAIL_COUNT + identifier);
    }

    /** token 摘要，避免明文 token 出现在 Redis 中 */
    private String digest(String token) {
        return Integer.toHexString(token == null ? 0 : token.hashCode());
    }
}
