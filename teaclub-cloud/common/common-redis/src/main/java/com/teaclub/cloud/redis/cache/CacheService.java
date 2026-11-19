package com.teaclub.cloud.redis.cache;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 通用缓存访问组件。
 *
 * <p><b>说明</b>：本组件提供缓存读写能力，但**不是资金一致性的保障**。
 * 钱包余额等资金数据的最终一致性由数据库事务与流水表保证，
 * 缓存只用于降低读放大。任何资金写入后必须主动失效缓存。
 */
@RequiredArgsConstructor
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 写入缓存。
     *
     * @param key   完整键
     * @param value 值
     * @param ttl   过期时间
     */
    public void set(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    /** 写入缓存（使用默认 TTL，由调用方指定键约定） */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /** 读取缓存 */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) redisTemplate.opsForValue().get(key);
    }

    /** 读取缓存（带类型转换） */
    public <T> T get(String key, Class<T> type) {
        Object v = redisTemplate.opsForValue().get(key);
        return type.isInstance(v) ? type.cast(v) : null;
    }

    /** 删除缓存 */
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    /** 批量删除 */
    public void delete(Collection<String> keys) {
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /** 按前缀批量删除（用 SCAN，避免 KEYS 阻塞） */
    public long deleteByPrefix(String prefix) {
        Set<String> keys = redisTemplate.keys(prefix + "*");
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        Long n = redisTemplate.delete(keys);
        return n == null ? 0L : n;
    }

    /** 是否存在 */
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /** 剩余过期秒数，-1 表示永久，-2 表示不存在 */
    public long ttl(String key) {
        Long t = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return t == null ? -2L : t;
    }

    /** 设置过期时间 */
    public void expire(String key, Duration ttl) {
        redisTemplate.expire(key, ttl);
    }

    /** 原子递增（用于登录失败计数等） */
    public long increment(String key, Duration ttl) {
        Long v = redisTemplate.opsForValue().increment(key);
        if (v != null && v == 1L) {
            redisTemplate.expire(key, ttl);
        }
        return v == null ? 0L : v;
    }

    /** 按前缀查询键 */
    public Set<String> keys(String prefix) {
        Set<String> keys = redisTemplate.keys(prefix + "*");
        return keys == null ? Set.of() : keys;
    }
}
