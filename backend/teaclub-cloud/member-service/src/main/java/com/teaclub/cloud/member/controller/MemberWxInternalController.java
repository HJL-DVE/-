package com.teaclub.cloud.member.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.member.dto.MemberWxBindRequest;
import com.teaclub.cloud.member.service.MemMemberOnboardingService;
import com.teaclub.cloud.member.vo.MemberWxBindVO;
import com.teaclub.cloud.member.vo.MemberWxMemberVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信登录绑定内部接口，仅供 auth-service 调用。
 *
 * <p>访问控制：由 {@code InternalTokenFilter} 校验 {@code X-Internal-Token}。
 */
@Tag(name = "内部接口", description = "微信登录绑定，服务间调用专用")
@RestController
@RequestMapping("/api/member/internal/wx")
@Slf4j
@RequiredArgsConstructor
public class MemberWxInternalController {

    private final MemMemberOnboardingService onboardingService;

    @Operation(summary = "按 openid 查询会员身份（内部调用）")
    @GetMapping("/member")
    public Result<MemberWxMemberVO> member(@RequestParam("openid") String openid,
                                           @RequestParam("brandId") Long brandId) {
        MemberWxMemberVO member = onboardingService.findByOpenid(openid, brandId);
        if (member == null) {
            return Result.fail(ResultCode.NOT_FOUND);
        }
        return Result.ok(member);
    }

    @Operation(summary = "openid find-or-bind 会员（内部调用）")
    @PostMapping("/bind")
    public Result<MemberWxBindVO> bind(@Valid @RequestBody MemberWxBindRequest request) {
        return Result.ok(onboardingService.bind(request));
    }
}
