package com.teaclub.cloud.marketing.controller;

import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.marketing.entity.MktCouponTemplate;
import com.teaclub.cloud.marketing.mapper.MktCouponTemplateMapper;
import com.teaclub.cloud.marketing.service.CouponService;
import com.teaclub.cloud.marketing.vo.CouponTemplateVO;
import com.teaclub.cloud.marketing.vo.CouponVO;
import com.teaclub.cloud.security.context.StoreScope;
import com.teaclub.cloud.security.context.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 优惠券管理端（Web 后台）。
 *
 * <p>权限串沿用 iam_db 中已存在的命名习惯；新增权限点由 DBA 迁移脚本补。
 */
@Tag(name = "优惠券管理", description = "模板维护、发放、查询")
@RestController
@RequestMapping("/api/admin/marketing/coupons")
@RequiredArgsConstructor
public class CouponAdminController {

    private final CouponService couponService;
    private final MktCouponTemplateMapper templateMapper;

    @Operation(summary = "优惠券模板分页/列表")
    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('marketing:coupon:list')")
    public Result<PageResult<CouponTemplateVO>> templates(@RequestParam(required = false) Integer pageNo,
                                                          @RequestParam(required = false) Integer pageSize,
                                                          @RequestParam(required = false) String couponType,
                                                          @RequestParam(required = false) String status) {
        var w = com.baomidou.mybatisplus.core.toolkit.Wrappers.<MktCouponTemplate>lambdaQuery()
                .eq(MktCouponTemplate::getBrandId, UserContext.require().getBrandId())
                .eq(MktCouponTemplate::getDeleted, 0)
                .orderByDesc(MktCouponTemplate::getId);
        if (couponType != null && !couponType.isBlank()) {
            w.eq(MktCouponTemplate::getCouponType, couponType);
        }
        if (status != null && !status.isBlank()) {
            w.eq(MktCouponTemplate::getStatus, status);
        }
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        var page = templateMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(no, size), w);
        List<CouponTemplateVO> vos = page.getRecords().stream().map(t -> {
            CouponTemplateVO vo = new CouponTemplateVO();
            vo.setId(t.getId());
            vo.setTemplateNo(t.getTemplateNo());
            vo.setCouponName(t.getCouponName());
            vo.setCouponType(t.getCouponType());
            vo.setCouponTypeLabel(safeTypeLabel(t.getCouponType()));
            vo.setScopeType(t.getScopeType());
            vo.setFaceValue(t.getFaceValue());
            vo.setDiscountRate(t.getDiscountRate());
            vo.setMaxDiscountAmount(t.getMaxDiscountAmount());
            vo.setThresholdAmount(t.getThresholdAmount());
            vo.setTotalCount(t.getTotalCount());
            vo.setIssuedCount(t.getIssuedCount());
            vo.setPerMemberLimit(t.getPerMemberLimit());
            vo.setValidType(t.getValidType());
            vo.setValidStart(t.getValidStart());
            vo.setValidEnd(t.getValidEnd());
            vo.setValidDays(t.getValidDays());
            vo.setNewMemberOnly(t.getNewMemberOnly());
            vo.setBirthdayOnly(t.getBirthdayOnly());
            vo.setStatus(t.getStatus());
            vo.setRemark(t.getRemark());
            vo.setCreateTime(t.getCreateTime());
            return vo;
        }).toList();
        return Result.ok(new PageResult<>(vos, page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "会员券查询（按会员或按券码）")
    @GetMapping("/instances")
    @PreAuthorize("hasAuthority('marketing:coupon:list')")
    public Result<List<CouponVO>> instances(@RequestParam(required = false) Long memberId,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) Integer limit) {
        if (memberId == null) {
            return Result.ok(List.of());
        }
        return Result.ok(couponService.listMine(memberId, status, limit == null ? 50 : limit));
    }

    @Operation(summary = "后台发放优惠券给会员")
    @PostMapping("/templates/{templateId}/grant")
    @PreAuthorize("hasAuthority('marketing:coupon:grant')")
    public Result<List<CouponVO>> grant(@PathVariable Long templateId,
                                        @RequestParam Long memberId,
                                        @RequestParam(defaultValue = "1") int qty) {
        // 后台发放不受「每人限领」约束，但仍受发行总量约束（tryIssueOne 保证）
        return Result.ok(couponService.receive(templateId, memberId, qty));
    }

    private static String safeTypeLabel(String code) {
        try {
            return com.teaclub.cloud.marketing.enums.CouponType.valueOf(code).getLabel();
        } catch (Exception e) {
            return code;
        }
    }
}
