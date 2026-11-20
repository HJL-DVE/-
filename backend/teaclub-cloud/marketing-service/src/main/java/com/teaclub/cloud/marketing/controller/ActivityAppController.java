package com.teaclub.cloud.marketing.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.marketing.service.ActivityService;
import com.teaclub.cloud.marketing.vo.ActivityVO;
import com.teaclub.cloud.security.context.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 会员端营销活动（小程序）。 */
@Tag(name = "会员端活动", description = "活动列表与参与")
@RestController
@RequestMapping("/api/marketing/activities")
@RequiredArgsConstructor
public class ActivityAppController {

    private final ActivityService activityService;

    @Operation(summary = "当前可参与的活动")
    @GetMapping
    public Result<List<ActivityVO>> list(@RequestParam(required = false) Long storeId) {
        return Result.ok(activityService.listActiveForMember(
                UserContext.requireMemberId(), storeId));
    }

    @Operation(summary = "参与活动（节日活动等主动领取型）")
    @PostMapping("/{id}/join")
    public Result<Boolean> join(@PathVariable Long id,
                                @RequestParam(required = false) Long storeId) {
        return Result.ok(activityService.join(id, UserContext.requireMemberId(), storeId));
    }
}
