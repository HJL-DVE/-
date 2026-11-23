package com.teaclub.cloud.marketing.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.marketing.service.ActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 营销活动内部触发接口（供 auth-service 注册后调用）。
 *
 * <p>新人礼包必须在注册成功后立即发放，因此走同步内部调用而非事件队列
 * （注册是低频操作，同步不影响性能，且能立刻反馈发奖结果）。
 */
@Tag(name = "营销活动内部接口")
@RestController
@RequestMapping("/api/marketing/internal/activities")
@RequiredArgsConstructor
public class ActivityInternalController {

    private final ActivityService activityService;

    @Operation(summary = "触发新人礼包活动")
    @PostMapping("/trigger-new-member")
    public Result<Integer> triggerNewMember(@RequestParam Long brandId,
                                            @RequestParam Long memberId) {
        return Result.ok(activityService.triggerNewMember(brandId, memberId));
    }

    @Operation(summary = "触发生日活动（单个会员，定时任务外部调用亦可）")
    @PostMapping("/trigger-birthday")
    public Result<Integer> triggerBirthday(@RequestParam Long brandId,
                                           @RequestParam Long memberId) {
        return Result.ok(activityService.triggerBirthday(brandId, memberId));
    }

    @Operation(summary = "触发沉睡召回活动")
    @PostMapping("/trigger-sleeping-recall")
    public Result<Integer> triggerSleepingRecall(@RequestParam Long brandId,
                                                 @RequestParam Long memberId) {
        return Result.ok(activityService.triggerSleepingRecall(brandId, memberId));
    }
}
