package com.teaclub.cloud.marketing.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.marketing.dto.ActivitySaveRequest;
import com.teaclub.cloud.marketing.service.ActivityService;
import com.teaclub.cloud.marketing.vo.ActivityVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 营销活动管理端。 */
@Tag(name = "营销活动管理", description = "8 类活动的配置与查询")
@RestController
@RequestMapping("/api/admin/marketing/activities")
@RequiredArgsConstructor
public class ActivityAdminController {

    private final ActivityService activityService;

    @Operation(summary = "活动列表")
    @GetMapping
    @PreAuthorize("hasAuthority('marketing:activity:list')")
    public Result<List<ActivityVO>> list(@RequestParam(required = false) String activityType,
                                         @RequestParam(required = false) String status) {
        return Result.ok(activityService.listAll(activityType, status));
    }

    @Operation(summary = "活动详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('marketing:activity:list')")
    public Result<ActivityVO> detail(@PathVariable Long id) {
        return Result.ok(activityService.detail(id));
    }

    @Operation(summary = "新建活动")
    @PostMapping
    @PreAuthorize("hasAuthority('marketing:activity:create')")
    public Result<Long> create(@Valid @RequestBody ActivitySaveRequest request) {
        return Result.ok(activityService.create(request));
    }

    @Operation(summary = "编辑活动")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('marketing:activity:update')")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody ActivitySaveRequest request) {
        activityService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除活动")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('marketing:activity:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        activityService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "启用/停用活动")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('marketing:activity:update')")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam String status) {
        activityService.changeStatus(id, status);
        return Result.ok();
    }
}
