package com.teaclub.cloud.redis.lock;

import com.teaclub.cloud.redis.constant.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Redis 分布式锁。
 *
 * <p>基于 {@code SET key value NX PX} + Lua 原子释放实现。
 *
 * <p><b>重要限制</b>：本锁<b>不是</b>钱包资金一致性的唯一保障。
 * 资金操作必须依赖数据库行锁（{@code SELECT ... FOR UPDATE}）与业务单号唯一索引，
 * 分布式锁仅用于降低并发冲突概率、避免重复处理。
 *
 * <p>释放时校验 value 是否为自己持有的 token，避免误删他人锁。
 */
@Component
@RequiredArgsConstructor
public class RedisDistributedLock {

    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLock.class);

    private final RedisTemplate<String, Object> redisTemplate;

    /** 解锁 Lua：比对 value 后删除 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) "
                    + "else return 0 end",
            Long.class);

    /**
     * 尝试加锁并执行。
     *
     * @param lockKey   锁的业务键（不含前缀）
     * @param waitTime  最长等待时间
     * @param leaseTime 锁持有时间（自动释放，防止死锁）
     * @param supplier  业务逻辑
     * @return 执行结果；未抢到锁时 acquired=false
     */
    public <T> LockResult<T> tryLock(String lockKey, Duration waitTime, Duration leaseTime,
                                     Supplier<T> supplier) {
        String key = RedisKeys.LOCK + lockKey;
        String token = UUID.randomUUID().toString();
        long deadline = System.currentTimeMillis() + waitTime.toMillis();

        boolean locked = false;
        try {
            while (System.currentTimeMillis() < deadline) {
                Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, token, leaseTime);
                if (Boolean.TRUE.equals(ok)) {
                    locked = true;
                    break;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (!locked) {
                log.warn("获取分布式锁超时: key={}", key);
                return LockResult.failed();
            }
            return LockResult.success(supplier.get());
        } finally {
            if (locked) {
                try {
                    redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), token);
                } catch (Exception e) {
                    log.error("释放分布式锁失败: key={}", key, e);
                }
            }
        }
    }

    /** 无等待加锁 */
    public <T> LockResult<T> tryLock(String lockKey, Duration leaseTime, Supplier<T> supplier) {
        return tryLock(lockKey, Duration.ZERO, leaseTime, supplier);
    }

    /**
     * 检查锁是否被占用（只读）。
     */
    public boolean isLocked(String lockKey) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.LOCK + lockKey));
    }
}
