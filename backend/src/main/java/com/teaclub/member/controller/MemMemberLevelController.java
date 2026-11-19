package com.teaclub.member.controller;

import com.teaclub.common.annotation.AuditLog;
import com.teaclub.common.api.Result;
import com.teaclub.member.dto.MemberLevelSaveRequest;
import com.teaclub.member.service.MemMemberLevelService;
import com.teaclub.member.vo.MemberLevelVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "会员等级管理", description = "等级规则、折扣与升级门槛")
@RestController
@RequestMapping("/api/admin/member/levels")
@RequiredArgsConstructor
public class MemMemberLevelController {

    private final MemMemberLevelService levelService;

    @Operation(summary = "会员等级列表")
    @GetMapping
    @PreAuthorize("hasAuthority('member:level:list')")
    public Result<List<MemberLevelVO>> list() {
        return Result.ok(levelService.listAll());
    }

    @Operation(summary = "会员等级详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('member:level:list')")
    public Result<MemberLevelVO> detail(@PathVariable Long id) {
        return Result.ok(levelService.detail(id));
    }

    @Operation(summary = "新增会员等级")
    @PostMapping
    @PreAuthorize("hasAuthority('member:level:create')")
    @AuditLog(module = "会员等级", value = "新增会员等级")
    public Result<Long> create(@Valid @RequestBody MemberLevelSaveRequest request) {
        return Result.ok(levelService.create(request));
    }

    @Operation(summary = "编辑会员等级")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('member:level:update')")
    @AuditLog(module = "会员等级", value = "编辑会员等级")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody MemberLevelSaveRequest request) {
        levelService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除会员等级")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('member:level:delete')")
    @AuditLog(module = "会员等级", value = "删除会员等级")
    public Result<Void> delete(@PathVariable Long id) {
        levelService.delete(id);
        return Result.ok();
    }
}
