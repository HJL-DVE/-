package com.teaclub.common.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存实现。生产环境使用（teaclub.cache.provider=redis）。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "teaclub.cache.provider", havingValue = "redis")
public class RedisCacheService implements CacheService {

    private final StringRedisTemplate redis;

    @Override
    public void set(String key, String value, long timeout, TimeUnit unit) {
        if (timeout > 0) {
            redis.opsForValue().set(key, value, timeout, unit);
        } else {
            redis.opsForValue().set(key, value);
        }
    }

    @Override
    public String get(String key) {
        return redis.opsForValue().get(key);
    }

    @Override
    public boolean delete(String key) {
        return Boolean.TRUE.equals(redis.delete(key));
    }

    @Override
    public boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit) {
        Boolean ok = timeout > 0
                ? redis.opsForValue().setIfAbsent(key, value, timeout, unit)
                : redis.opsForValue().setIfAbsent(key, value);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public long increment(String key, long delta) {
        Long v = redis.opsForValue().increment(key, delta);
        return v == null ? 0 : v;
    }
}
