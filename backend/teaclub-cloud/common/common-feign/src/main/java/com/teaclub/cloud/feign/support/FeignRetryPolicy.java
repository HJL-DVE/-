package com.teaclub.cloud.feign.support;

import feign.RetryableException;
import feign.Retryer;

import java.util.concurrent.TimeUnit;

/**
 * 可控重试策略（Feign 13 接口）。
 *
 * <p><b>使用约束</b>：仅允许用于<b>幂等的只读操作</b>。
 * 资金扣款、支付创建、退款等有副作用的接口，
 * 必须依赖业务单号幂等 + 明确的操作结果查询，<b>不得</b>依赖自动重试。
 */
public class FeignRetryPolicy implements Retryer {

    private final int maxAttempts;
    private final long periodMillis;
    private final long maxPeriodMillis;

    private int attempt;

    public FeignRetryPolicy(int maxAttempts, long periodMillis, long maxPeriodMillis) {
        this.maxAttempts = Math.max(0, maxAttempts);
        this.periodMillis = Math.max(1, periodMillis);
        this.maxPeriodMillis = Math.max(this.periodMillis, maxPeriodMillis);
    }

    @Override
    public void continueOrPropagate(RetryableException e) {
        if (e.getCause() instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            throw e;
        }
        if (attempt >= maxAttempts) {
            throw e;
        }
        attempt++;
        try {
            TimeUnit.MILLISECONDS.sleep(nextDelay());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    @Override
    public Retryer clone() {
        return new FeignRetryPolicy(maxAttempts, periodMillis, maxPeriodMillis);
    }

    private long nextDelay() {
        return (long) Math.min(maxPeriodMillis, periodMillis * Math.max(1, attempt));
    }
}
