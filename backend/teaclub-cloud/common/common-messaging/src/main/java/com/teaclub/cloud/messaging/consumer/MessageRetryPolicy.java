package com.teaclub.cloud.messaging.consumer;

/**
 * 消息重试策略。
 *
 * <p>基于 RabbitMQ 的死信队列实现指数退避：
 * <pre>
 * 业务队列 --(nack, requeue=false)--> 重试队列(TTL) --(TTL 到期)--> 业务队列
 * </pre>
 * 超过最大重试次数后进入最终死信队列（DLQ）。
 */
public class MessageRetryPolicy {

    private final int maxAttempts;
    private final long[] delayMillis;

    /**
     * @param maxAttempts 最大重试次数
     * @param delays      每次重试的延迟（毫秒），长度应等于 maxAttempts
     */
    public MessageRetryPolicy(int maxAttempts, long[] delays) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts 必须大于 0");
        }
        if (delays == null || delays.length < maxAttempts) {
            throw new IllegalArgumentException("delays 长度必须不小于 maxAttempts");
        }
        this.maxAttempts = maxAttempts;
        this.delayMillis = delays.clone();
    }

    /** 默认策略：3 次重试，间隔 1s / 5s / 30s */
    public static MessageRetryPolicy defaultPolicy() {
        return new MessageRetryPolicy(3, new long[]{1000L, 5000L, 30000L});
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    /**
     * 是否还可以重试。
     *
     * @param attempt 已重试次数（从 1 开始）
     */
    public boolean canRetry(int attempt) {
        return attempt < maxAttempts;
    }

    /**
     * 本次重试的延迟时长。
     *
     * @param attempt 已重试次数（从 1 开始）
     */
    public long delayOf(int attempt) {
        int idx = Math.min(Math.max(attempt, 1), delayMillis.length) - 1;
        return delayMillis[idx];
    }
}
