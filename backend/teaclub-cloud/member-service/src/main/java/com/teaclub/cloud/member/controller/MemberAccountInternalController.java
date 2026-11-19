package com.teaclub.cloud.member.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.member.dto.MemberRegisterRequest;
import com.teaclub.cloud.member.service.MemMemberOnboardingService;
import com.teaclub.cloud.member.service.MemMemberService;
import com.teaclub.cloud.member.vo.MemberCredentialVO;
import com.teaclub.cloud.member.vo.MemberVO;
import com.teaclub.cloud.member.vo.MemberWxBindVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会员账号内部接口，仅供 auth-service 调用。
 *
 * <p>auth-service 不持有 member_db，注册与密码校验所需的数据都经此接口往返：
 * 密码散列在 auth-service 生成/比对，本服务只负责落库与读取。
 */
@Tag(name = "内部接口", description = "会员账号注册与凭据查询，服务间调用专用")
@RestController
@RequestMapping("/api/member/internal")
@Slf4j
@RequiredArgsConstructor
public class MemberAccountInternalController {

    private final MemMemberOnboardingService onboardingService;
    private final MemMemberService memberService;

    @Operation(summary = "手机号注册会员（内部调用）")
    @PostMapping("/register")
    public Result<MemberWxBindVO> register(@Valid @RequestBody MemberRegisterRequest request) {
        return Result.ok(onboardingService.register(request));
    }

    @Operation(summary = "按手机号查询登录凭据（内部调用）")
    @GetMapping("/credential")
    public Result<MemberCredentialVO> credential(@RequestParam("phone") String phone,
                                                 @RequestParam("brandId") Long brandId) {
        MemberCredentialVO credential = onboardingService.credential(phone, brandId);
        if (credential == null) {
            return Result.fail(ResultCode.MEMBER_NOT_FOUND);
        }
        return Result.ok(credential);
    }

    @Operation(summary = "查询会员档案（内部调用）")
    @GetMapping("/member/{memberId}")
    public Result<MemberVO> profile(@PathVariable("memberId") Long memberId) {
        return Result.ok(memberService.profile(memberId));
    }
}
