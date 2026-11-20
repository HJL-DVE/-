package com.teaclub.cloud.marketing.service;

import com.teaclub.cloud.marketing.service.CouponService;
import com.teaclub.cloud.marketing.service.PointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 营销定时任务：优惠券过期 + 积分批次过期。
 *
 * <p><b>多实例提醒</b>：与项目里其它补偿任务一样，这里没有分布式锁。
 * 单机部署没问题；多实例部署时会出现并发执行，
 * 后果是同一批数据被处理多次 —— 券过期与积分过期都是幂等 UPDATE，
 * 重复执行不会算错，但会浪费连接。真要上多实例需补 ShedLock 或 Redis 锁。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketingScheduler {

    private final CouponService couponService;
    private final PointsService pointsService;

    @Value("${teaclub.marketing.coupon-expire-enabled:true}")
    private boolean couponExpireEnabled;

    @Value("${teaclub.marketing.points-expire-enabled:true}")
    private boolean pointsExpireEnabled;

    /** 优惠券过期：每 10 分钟。 */
    @Scheduled(fixedDelayString = "${teaclub.marketing.coupon-expire-ms:600000}")
    public void expireCoupons() {
        if (!couponExpireEnabled) {
            return;
        }
        try {
            int n = couponService.expireBatch(200);
            if (n > 0) {
                log.info("[SCHEDULER] 优惠券过期处理完成 count={}", n);
            }
        } catch (Exception e) {
            log.error("[SCHEDULER] 优惠券过期处理失败", e);
        }
    }

    /** 积分批次过期：每 30 分钟。 */
    @Scheduled(fixedDelayString = "${teaclub.marketing.points-expire-ms:1800000}")
    public void expirePoints() {
        if (!pointsExpireEnabled) {
            return;
        }
        try {
            pointsService.expireBatches(200);
        } catch (Exception e) {
            log.error("[SCHEDULER] 积分过期处理失败", e);
        }
    }
}
