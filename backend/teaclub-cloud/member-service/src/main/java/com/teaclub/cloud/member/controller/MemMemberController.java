package com.teaclub.cloud.member.controller;

import com.teaclub.cloud.web.annotation.AuditLog;
import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.member.dto.MemberQuery;
import com.teaclub.cloud.member.dto.MemberUpdateRequest;
import com.teaclub.cloud.member.service.MemMemberService;
import com.teaclub.cloud.member.vo.MemberVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "会员管理", description = "品牌级会员查询、资料维护与状态管理")
@RestController
@RequestMapping("/api/admin/member/members")
@RequiredArgsConstructor
public class MemMemberController {

    private final MemMemberService memberService;

    @Operation(summary = "会员分页查询（受门店数据权限约束）")
    @GetMapping
    @PreAuthorize("hasAuthority('member:list')")
    public Result<PageResult<MemberVO>> page(MemberQuery query) {
        return Result.ok(memberService.page(query));
    }

    @Operation(summary = "按手机号查询会员")
    @GetMapping("/by-phone")
    @PreAuthorize("hasAuthority('member:list')")
    public Result<MemberVO> byPhone(@RequestParam String phone) {
        return Result.ok(memberService.findByPhone(phone));
    }

    @Operation(summary = "会员详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('member:detail')")
    public Result<MemberVO> detail(@PathVariable Long id) {
        return Result.ok(memberService.detail(id));
    }

    @Operation(summary = "编辑会员资料")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('member:update')")
    @AuditLog(module = "会员管理", value = "编辑会员资料")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody MemberUpdateRequest request) {
        memberService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "冻结/解冻会员")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('member:status')")
    @AuditLog(module = "会员管理", value = "变更会员状态")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null || !status.matches("^(NORMAL|FROZEN)$")) {
            return Result.fail(10002, "状态只能是 NORMAL 或 FROZEN");
        }
        memberService.updateStatus(id, status, body.get("freezeReason"));
        return Result.ok();
    }

    @Operation(summary = "删除会员")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('member:delete')")
    @AuditLog(module = "会员管理", value = "删除会员")
    public Result<Void> delete(@PathVariable Long id) {
        memberService.delete(id);
        return Result.ok();
    }
}
