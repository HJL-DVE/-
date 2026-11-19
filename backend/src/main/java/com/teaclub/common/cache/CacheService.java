package com.teaclub.common.cache;

import java.util.concurrent.TimeUnit;

/**
 * 缓存抽象。开发环境无 Redis 时使用本地实现，生产切换 Redis。
 */
public interface CacheService {

    void set(String key, String value, long timeout, TimeUnit unit);

    String get(String key);

    boolean delete(String key);

    /**
     * 当且仅当 key 不存在时写入，返回是否写入成功（用于幂等）。
     */
    boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit);

    long increment(String key, long delta);
}
