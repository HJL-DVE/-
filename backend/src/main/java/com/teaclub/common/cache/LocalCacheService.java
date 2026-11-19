package com.teaclub.common.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 进程内缓存实现。用于本机开发/演示环境（无 Redis 时）。
 *
 * <p>注意：仅适用于单实例部署，生产环境应使用 Redis 实现。
 */
@Component
@ConditionalOnProperty(name = "teaclub.cache.provider", havingValue = "local", matchIfMissing = true)
public class LocalCacheService implements CacheService {

    private record Entry(String value, long expireAt) {
        boolean expired() {
            return expireAt > 0 && System.currentTimeMillis() > expireAt;
        }
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    private static long deadline(long timeout, TimeUnit unit) {
        return timeout <= 0 ? 0 : System.currentTimeMillis() + unit.toMillis(timeout);
    }

    @Override
    public void set(String key, String value, long timeout, TimeUnit unit) {
        store.put(key, new Entry(value, deadline(timeout, unit)));
    }

    @Override
    public String get(String key) {
        Entry e = store.get(key);
        if (e == null) {
            return null;
        }
        if (e.expired()) {
            store.remove(key);
            return null;
        }
        return e.value();
    }

    @Override
    public boolean delete(String key) {
        counters.remove(key);
        return store.remove(key) != null;
    }

    @Override
    public boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit) {
        Entry fresh = new Entry(value, deadline(timeout, unit));
        Entry prev = store.compute(key, (k, old) -> (old == null || old.expired()) ? fresh : old);
        return prev == fresh;
    }

    @Override
    public long increment(String key, long delta) {
        return counters.computeIfAbsent(key, k -> new AtomicLong(0)).addAndGet(delta);
    }
}
