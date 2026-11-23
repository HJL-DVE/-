package com.teaclub.cloud.marketing.event;

/**
 * 签到完成事件（进程内事件）。
 *
 * <p>用事件而非直接注入来解耦 PointsServiceImpl 与 ActivityServiceImpl：
 * 两者本就互相需要（活动发奖要加积分、签到要触发活动），直接注入会形成循环依赖。
 */
public class SignInCompletedEvent {

    private final Long brandId;
    private final Long memberId;
    /** 本次签到后的连续天数 */
    private final int continuousDays;

    public SignInCompletedEvent(Long brandId, Long memberId, int continuousDays) {
        this.brandId = brandId;
        this.memberId = memberId;
        this.continuousDays = continuousDays;
    }

    public Long getBrandId() {
        return brandId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public int getContinuousDays() {
        return continuousDays;
    }
}
