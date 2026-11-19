package com.teaclub.cloud.member.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.member.mapper.MemMemberMapper;
import com.teaclub.cloud.member.service.support.MemberVisibilityResolver;
import com.teaclub.cloud.member.vo.MemberBriefVO;
import com.teaclub.cloud.member.vo.MemberLevelBriefVO;
import com.teaclub.cloud.security.context.StoreScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * 会员简要信息内部接口。
 *
 * <p>供 wallet-service / order-service 在展示层拼装会员昵称、手机号、等级名，
 * 以及按门店范围过滤会员集合使用。**只读**，不修改任何数据。
 *
 * <p>路径必须与 wallet-service 的 {@code MemberInternalClient} 严格一致
 * （{@code /api/member/internal} 前缀 + 下列子路径），否则 Feign 侧会 404。
 */
@Tag(name = "会员内部接口", description = "跨服务只读查询")
@RestController
@RequestMapping("/api/member/internal")
@RequiredArgsConstructor
public class MemberBriefInternalController {

    private final MemMemberMapper memberMapper;
    private final MemberVisibilityResolver visibilityResolver;

    @Operation(summary = "单个会员简要信息")
    @GetMapping("/user/{memberId}/brief")
    public Result<MemberBriefVO> brief(@PathVariable("memberId") Long memberId) {
        return Result.ok(memberMapper.selectBriefById(memberId));
    }

    @Operation(summary = "批量会员简要信息")
    @PostMapping("/users/batch")
    public Result<List<MemberBriefVO>> batch(@RequestBody List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Result.ok(List.of());
        }
        return Result.ok(memberMapper.selectBriefByIds(memberIds));
    }

    @Operation(summary = "批量会员等级信息")
    @PostMapping("/levels/batch")
    public Result<List<MemberLevelBriefVO>> levelsBatch(@RequestBody List<Long> levelIds) {
        if (levelIds == null || levelIds.isEmpty()) {
            return Result.ok(List.of());
        }
        return Result.ok(memberMapper.selectLevelBriefByIds(levelIds));
    }

    @Operation(summary = "门店范围内可见的会员 ID")
    @GetMapping("/visible-member-ids")
    public Result<List<Long>> visibleMemberIds(@RequestParam("brandId") Long brandId,
                                               @RequestParam("storeIds") Collection<Long> storeIds) {
        StoreScope scope = StoreScope.limited(new HashSet<>(storeIds));
        return Result.ok(visibilityResolver.resolveVisibleMemberIds(brandId, scope));
    }

    @Operation(summary = "按手机号/昵称/会员号检索会员 ID")
    @GetMapping("/member-ids")
    public Result<List<Long>> memberIds(@RequestParam("brandId") Long brandId,
                                        @RequestParam(value = "phone", required = false) String phone,
                                        @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(memberMapper.selectIdsByPhoneOrKeyword(brandId, phone, keyword));
    }
}
