package com.teaclub.cloud.redis.lock;

/**
 * 分布式锁执行结果。
 *
 * @param acquired 是否成功获取锁
 * @param result   业务执行结果（未获取锁时为 null）
 */
public record LockResult<T>(boolean acquired, T result) {

    public static <T> LockResult<T> success(T result) {
        return new LockResult<>(true, result);
    }

    public static <T> LockResult<T> failed() {
        return new LockResult<>(false, null);
    }
}
