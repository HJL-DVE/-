package com.teaclub.cloud.marketing.scheduler;

import com.teaclub.cloud.marketing.client.MemberSnapshotClient;
import com.teaclub.cloud.marketing.service.ActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 营销活动自动触发调度器。
 *
 * <p>补齐服务层已实现但此前**没有任何调用方**的两类活动触发：
 * <ul>
 *   <li>{@code BIRTHDAY}：每天扫一次当月生日会员，发放生日券/生日积分；</li>
 *   <li>{@code SLEEPING_RECALL}：每天扫一次沉睡会员，发放召回券。</li>
 * </ul>
 *
 * <p>发奖由 {@code doGrant} 完成，参与记录唯一键（activity_id + member_id + biz_no）
 * 保证同一会员同一天只发一次，因此定时任务重复执行无副作用。
 *
 * <p>业务规则（沉睡天数）从容器的 activity config_json 读取，此处用默认 30 天；
 * 若活动配置了 sleepingDays 则以其为准。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityTriggerScheduler {

    /** 生日活动：每天 09:05 执行（避开整点高峰） */
    private static final String CRON_BIRTHDAY = "0 5 9 * * ?";

    /** 沉睡召回：每天 10:10 执行 */
    private static final String CRON_SLEEPING = "0 10 10 * * ?";

    /** 默认沉睡天数（活动未配置时使用） */
    private static final int DEFAULT_SLEEPING_DAYS = 30;

    private final ActivityService activityService;
    private final MemberSnapshotClient memberSnapshotClient;

    /** 生日活动触发：当月生日会员逐个发奖。 */
    @Scheduled(cron = CRON_BIRTHDAY)
    public void triggerBirthdayActivities() {
        int month = LocalDate.now().getMonthValue();
        List<Long> memberIds;
        try {
            var resp = memberSnapshotClient.birthdayMembers(1L, month);
            if (resp == null || resp.getCode() != 0 || resp.getData() == null) {
                log.warn("[ACT-TRIGGER] 获取生日会员失败 month={} code={}", month,
                        resp == null ? null : resp.getCode());
                return;
            }
            memberIds = resp.getData();
        } catch (Exception e) {
            log.warn("[ACT-TRIGGER] 调用会员服务异常 month={} err={}", month, e.getMessage());
            return;
        }
        if (memberIds.isEmpty()) {
            log.info("[ACT-TRIGGER] 本月无生日会员 month={}", month);
            return;
        }
        int granted = 0;
        for (Long memberId : memberIds) {
            try {
                granted += activityService.triggerBirthday(1L, memberId);
            } catch (Exception e) {
                log.warn("[ACT-TRIGGER] 生日发奖失败 memberId={} err={}", memberId, e.getMessage());
            }
        }
        log.info("[ACT-TRIGGER] 生日活动完成 month={} 会员数={} 发奖次数={}",
                month, memberIds.size(), granted);
    }

    /** 沉睡会员召回触发。 */
    @Scheduled(cron = CRON_SLEEPING)
    public void triggerSleepingRecallActivities() {
        List<Long> memberIds;
        try {
            var resp = memberSnapshotClient.sleepingMembers(1L, DEFAULT_SLEEPING_DAYS);
            if (resp == null || resp.getCode() != 0 || resp.getData() == null) {
                log.warn("[ACT-TRIGGER] 获取沉睡会员失败 code={}", resp == null ? null : resp.getCode());
                return;
            }
            memberIds = resp.getData();
        } catch (Exception e) {
            log.warn("[ACT-TRIGGER] 调用会员服务异常 err={}", e.getMessage());
            return;
        }
        if (memberIds.isEmpty()) {
            log.info("[ACT-TRIGGER] 无沉睡会员");
            return;
        }
        int granted = 0;
        for (Long memberId : memberIds) {
            try {
                granted += activityService.triggerSleepingRecall(1L, memberId);
            } catch (Exception e) {
                log.warn("[ACT-TRIGGER] 沉睡召回发奖失败 memberId={} err={}", memberId, e.getMessage());
            }
        }
        log.info("[ACT-TRIGGER] 沉睡召回完成 会员数={} 发奖次数={}", memberIds.size(), granted);
    }
}
