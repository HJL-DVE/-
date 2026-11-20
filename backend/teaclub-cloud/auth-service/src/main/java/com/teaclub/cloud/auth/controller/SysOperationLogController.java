package com.teaclub.cloud.auth.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teaclub.cloud.auth.entity.SysOperationLog;
import com.teaclub.cloud.auth.mapper.SysOperationLogMapper;
import com.teaclub.cloud.core.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 操作日志查询（只读）。
 *
 * <p>历史单体遗留的 sys_operation_log 表（1241 条真实数据），
 * 微服务化后各服务尚未接入写日志的 AOP；本接口先提供只读查询，
 * 让「系统管理 → 操作日志」页面展示既有数据。
 */
@Tag(name = "操作日志", description = "只读查询历史操作日志")
@RestController
@RequestMapping("/api/admin/system/logs")
@RequiredArgsConstructor
public class SysOperationLogController {

    private final SysOperationLogMapper logMapper;

    @Operation(summary = "分页查询（可按模块/操作人/关键字过滤）")
    @GetMapping
    @PreAuthorize("hasAuthority('system:log:list')")
    public Result<Map<String, Object>> page(
            @RequestParam(required = false, defaultValue = "1") Integer pageNo,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) Boolean success) {

        var w = Wrappers.<SysOperationLog>lambdaQuery()
                .eq(SysOperationLog::getBrandId, 1L)
                .orderByDesc(SysOperationLog::getCreateTime);
        if (module != null && !module.isBlank()) {
            w.like(SysOperationLog::getModule, module);
        }
        if (operator != null && !operator.isBlank()) {
            w.like(SysOperationLog::getOperatorName, operator);
        }
        if (success != null) {
            w.eq(SysOperationLog::getSuccess, success);
        }
        Page<SysOperationLog> page = logMapper.selectPage(
                new Page<>(pageNo, Math.min(pageSize, 100)), w);

        return Result.ok(Map.of(
                "records", page.getRecords(),
                "total", page.getTotal(),
                "pageNo", page.getCurrent(),
                "pageSize", page.getSize()));
    }
}