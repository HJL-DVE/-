package com.teaclub.cloud.marketing.service.impl;

import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.core.util.BizNoGenerator;
import com.teaclub.cloud.marketing.dto.CouponTemplateSaveRequest;
import com.teaclub.cloud.marketing.entity.MktCoupon;
import com.teaclub.cloud.marketing.entity.MktCouponTemplate;
import com.teaclub.cloud.marketing.enums.CouponScopeType;
import com.teaclub.cloud.marketing.enums.CouponType;
import com.teaclub.cloud.marketing.mapper.MktCouponMapper;
import com.teaclub.cloud.marketing.mapper.MktCouponTemplateMapper;
import com.teaclub.cloud.marketing.service.CouponTemplateService;
import com.teaclub.cloud.marketing.vo.CouponTemplateVO;
import com.teaclub.cloud.security.context.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 优惠券模板管理实现。
 *
 * <p><b>为什么已发行模板不允许改关键字段</b>：
 * 券实例在领取时会把面额/门槛/折扣率<b>冗余</b>到 {@code mkt_coupon}，
 * 改模板不会影响已发的券，但会造成"同一个模板下不同批次的券权益不一致"，
 * 对账时无法解释。因此已发行（issuedCount > 0）的模板只允许改非资金字段。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponTemplateServiceImpl implements CouponTemplateService {

    private final MktCouponTemplateMapper templateMapper;
    private final MktCouponMapper couponMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CouponTemplateSaveRequest request) {
        validate(request);
        MktCouponTemplate t = new MktCouponTemplate();
        t.setBrandId(brandId());
        t.setTemplateNo(BizNoGenerator.of("CT"));
        applyRequest(t, request);
        t.setIssuedCount(0);
        t.setDeleted(0);
        templateMapper.insert(t);
        saveRelations(t.getId(), request);
        log.info("[COUPON-TPL] 新建模板 id={} no={} name={} type={}",
                t.getId(), t.getTemplateNo(), t.getCouponName(), t.getCouponType());
        return t.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CouponTemplateSaveRequest request) {
        MktCouponTemplate t = templateMapper.selectById(id);
        if (t == null || t.getDeleted() == 1) {
            throw new BizException(ResultCode.NOT_FOUND, "优惠券模板不存在");
        }
        int issued = t.getIssuedCount() == null ? 0 : t.getIssuedCount();
        if (issued > 0) {
            // 已发行：只允许改非资金字段
            MktCouponTemplate patch = new MktCouponTemplate();
            patch.setId(id);
            patch.setCouponName(request.getCouponName());
            patch.setTotalCount(request.getTotalCount());
            patch.setStatus(request.getStatus());
            patch.setRemark(request.getRemark());
            patch.setPerMemberLimit(request.getPerMemberLimit());
            templateMapper.updateById(patch);
            log.info("[COUPON-TPL] 已发行模板仅更新非资金字段 id={} issued={}", id, issued);
            return;
        }
        applyRequest(t, request);
        t.setId(id);
        templateMapper.updateById(t);
        // 关系表全量重建
        jdbcTemplate.update("DELETE FROM mkt_coupon_template_store WHERE template_id = ?", id);
        jdbcTemplate.update("DELETE FROM mkt_coupon_template_spu WHERE template_id = ?", id);
        jdbcTemplate.update("DELETE FROM mkt_coupon_template_level WHERE template_id = ?", id);
        saveRelations(id, request);
        log.info("[COUPON-TPL] 更新模板 id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MktCouponTemplate t = templateMapper.selectById(id);
        if (t == null || t.getDeleted() == 1) {
            throw new BizException(ResultCode.NOT_FOUND, "优惠券模板不存在");
        }
        // 已发出去的券还在会员手里，模板不能删，只能停用
        Integer issued = t.getIssuedCount();
        if (issued != null && issued > 0) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "该模板已发行 " + issued + " 张，不能删除；请改为停用");
        }
        MktCouponTemplate patch = new MktCouponTemplate();
        patch.setId(id);
        patch.setDeleted(1);
        patch.setStatus("DISABLED");
        templateMapper.updateById(patch);
        log.info("[COUPON-TPL] 删除模板 id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String status) {
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status) && !"DRAFT".equals(status)) {
            throw new BizException(ResultCode.PARAM_INVALID, "状态只能是 DRAFT/ENABLED/DISABLED");
        }
        MktCouponTemplate t = templateMapper.selectById(id);
        if (t == null || t.getDeleted() == 1) {
            throw new BizException(ResultCode.NOT_FOUND, "优惠券模板不存在");
        }
        MktCouponTemplate patch = new MktCouponTemplate();
        patch.setId(id);
        patch.setStatus(status);
        templateMapper.updateById(patch);
    }

    @Override
    public CouponTemplateVO detail(Long id) {
        MktCouponTemplate t = templateMapper.selectById(id);
        if (t == null || t.getDeleted() == 1) {
            throw new BizException(ResultCode.NOT_FOUND, "优惠券模板不存在");
        }
        CouponTemplateVO vo = new CouponTemplateVO();
        vo.setId(t.getId());
        vo.setTemplateNo(t.getTemplateNo());
        vo.setCouponName(t.getCouponName());
        vo.setCouponType(t.getCouponType());
        try {
            vo.setCouponTypeLabel(CouponType.valueOf(t.getCouponType()).getLabel());
        } catch (Exception e) {
            vo.setCouponTypeLabel(t.getCouponType());
        }
        vo.setScopeType(t.getScopeType());
        vo.setFaceValue(t.getFaceValue());
        vo.setDiscountRate(t.getDiscountRate());
        vo.setMaxDiscountAmount(t.getMaxDiscountAmount());
        vo.setThresholdAmount(t.getThresholdAmount());
        vo.setExchangeSpuId(t.getExchangeSpuId());
        vo.setExchangeSkuId(t.getExchangeSkuId());
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
        vo.setStoreIds(templateMapper.selectStoreScope(id));
        vo.setSpuIds(templateMapper.selectSpuScope(id));
        vo.setLevelIds(templateMapper.selectLevelScope(id));
        return vo;
    }

    // ==================== 内部 ====================

    private void validate(CouponTemplateSaveRequest r) {
        CouponType type;
        try {
            type = CouponType.valueOf(r.getCouponType());
        } catch (Exception e) {
            throw new BizException(ResultCode.PARAM_INVALID, "不支持的券类型: " + r.getCouponType());
        }
        if (type.isDiscount()) {
            int rate = r.getDiscountRate() == null ? 10000 : r.getDiscountRate();
            if (rate <= 0 || rate >= 10000) {
                throw new BizException(ResultCode.PARAM_INVALID, "折扣率必须在 0~10000 之间（不含两端）");
            }
        } else if (type.isExchange()) {
            if (r.getExchangeSpuId() == null && r.getExchangeSkuId() == null) {
                throw new BizException(ResultCode.PARAM_INVALID, "兑换券必须指定可兑换的 SPU 或 SKU");
            }
        } else {
            long face = r.getFaceValue() == null ? 0L : r.getFaceValue();
            if (face <= 0) {
                throw new BizException(ResultCode.PARAM_INVALID, "面额必须大于 0");
            }
        }
        if (CouponType.FULL_REDUCTION.name().equals(r.getCouponType())) {
            long th = r.getThresholdAmount() == null ? 0L : r.getThresholdAmount();
            if (th <= 0) {
                throw new BizException(ResultCode.PARAM_INVALID, "满减券必须设置满减门槛");
            }
            long face = r.getFaceValue() == null ? 0L : r.getFaceValue();
            if (face >= th) {
                throw new BizException(ResultCode.PARAM_INVALID, "面额必须小于满减门槛");
            }
        }
        if (CouponScopeType.STORE.name().equals(r.getScopeType())
                && (r.getStoreIds() == null || r.getStoreIds().isEmpty())) {
            throw new BizException(ResultCode.PARAM_INVALID, "门店专属券必须配置适用门店");
        }
        if (CouponScopeType.GOODS.name().equals(r.getScopeType())
                && (r.getSpuIds() == null || r.getSpuIds().isEmpty())) {
            throw new BizException(ResultCode.PARAM_INVALID, "商品专属券必须配置适用商品");
        }
    }

    private void applyRequest(MktCouponTemplate t, CouponTemplateSaveRequest r) {
        t.setCouponName(r.getCouponName());
        t.setCouponType(r.getCouponType());
        t.setScopeType(r.getScopeType() == null ? "ALL" : r.getScopeType());
        t.setFaceValue(r.getFaceValue() == null ? 0L : r.getFaceValue());
        t.setDiscountRate(r.getDiscountRate() == null ? 10000 : r.getDiscountRate());
        t.setMaxDiscountAmount(r.getMaxDiscountAmount());
        t.setThresholdAmount(r.getThresholdAmount() == null ? 0L : r.getThresholdAmount());
        t.setExchangeSpuId(r.getExchangeSpuId());
        t.setExchangeSkuId(r.getExchangeSkuId());
        t.setTotalCount(r.getTotalCount() == null ? 0 : r.getTotalCount());
        t.setPerMemberLimit(r.getPerMemberLimit() == null ? 1 : r.getPerMemberLimit());
        t.setValidType(r.getValidType() == null ? "RELATIVE" : r.getValidType());
        t.setValidStart(r.getValidStart());
        t.setValidEnd(r.getValidEnd());
        t.setValidDays(r.getValidDays());
        t.setNewMemberOnly(r.getNewMemberOnly() == null ? 0 : r.getNewMemberOnly());
        t.setBirthdayOnly(r.getBirthdayOnly() == null ? 0 : r.getBirthdayOnly());
        t.setStatus(r.getStatus() == null ? "DRAFT" : r.getStatus());
        t.setRemark(r.getRemark());
        // 新人券 / 生日券 顺便把标记同步上，避免两处配置不一致
        if (CouponType.NEW_MEMBER.name().equals(r.getCouponType())) {
            t.setNewMemberOnly(1);
        }
        if (CouponType.BIRTHDAY.name().equals(r.getCouponType())) {
            t.setBirthdayOnly(1);
        }
    }

    private void saveRelations(Long templateId, CouponTemplateSaveRequest r) {
        Long brand = brandId();
        if (r.getStoreIds() != null) {
            for (Long sid : r.getStoreIds()) {
                jdbcTemplate.update(
                        "INSERT IGNORE INTO mkt_coupon_template_store (brand_id, template_id, store_id) VALUES (?,?,?)",
                        brand, templateId, sid);
            }
        }
        if (r.getSpuIds() != null) {
            for (Long spu : r.getSpuIds()) {
                jdbcTemplate.update(
                        "INSERT IGNORE INTO mkt_coupon_template_spu (brand_id, template_id, spu_id) VALUES (?,?,?)",
                        brand, templateId, spu);
            }
        }
        if (r.getLevelIds() != null) {
            for (Long lid : r.getLevelIds()) {
                jdbcTemplate.update(
                        "INSERT IGNORE INTO mkt_coupon_template_level (brand_id, template_id, level_id) VALUES (?,?,?)",
                        brand, templateId, lid);
            }
        }
    }

    private Long brandId() {
        Long b = UserContext.currentBrandIdOrNull();
        return b == null ? 1L : b;
    }
}
