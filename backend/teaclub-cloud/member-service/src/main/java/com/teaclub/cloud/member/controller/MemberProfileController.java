package com.teaclub.cloud.member.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.member.dto.MemberSelfUpdateRequest;
import com.teaclub.cloud.member.service.MemMemberLevelService;
import com.teaclub.cloud.member.service.MemMemberService;
import com.teaclub.cloud.member.vo.MemberLevelVO;
import com.teaclub.cloud.member.vo.MemberVO;
import com.teaclub.cloud.security.context.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会员端资料接口（小程序）。
 *
 * <p>会员身份由网关解析会员令牌后以 {@code X-Member-Id} 注入，
 * 接口内用 {@link UserContext#requireMemberId()} 取，缺失即 401；
 * 不使用 {@code @PreAuthorize}（会员端没有管理端权限点）。
 */
@Tag(name = "会员端-资料", description = "小程序会员资料与等级")
@RestController
@RequestMapping("/api/member")
@Slf4j
@RequiredArgsConstructor
public class MemberProfileController {

    private final MemMemberService memberService;
    private final MemMemberLevelService levelService;

    @Operation(summary = "当前会员资料")
    @GetMapping("/profile")
    public Result<MemberVO> profile() {
        return Result.ok(memberService.profile(UserContext.requireMemberId()));
    }

    @Operation(summary = "修改当前会员资料")
    @PutMapping("/profile")
    public Result<Void> updateProfile(@Valid @RequestBody MemberSelfUpdateRequest request) {
        memberService.updateProfile(UserContext.requireMemberId(), request);
        return Result.ok();
    }

    @Operation(summary = "会员等级列表")
    @GetMapping("/levels")
    public Result<List<MemberLevelVO>> levels() {
        return Result.ok(levelService.listForMember(UserContext.requireMemberId()));
    }
}
