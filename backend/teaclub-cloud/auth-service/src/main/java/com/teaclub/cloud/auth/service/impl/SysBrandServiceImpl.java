package com.teaclub.cloud.auth.service.impl;

import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.UserContext;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.auth.dto.BrandUpdateRequest;
import com.teaclub.cloud.auth.entity.SysBrand;
import com.teaclub.cloud.auth.mapper.SysBrandMapper;
import com.teaclub.cloud.auth.service.SysBrandService;
import com.teaclub.cloud.auth.vo.BrandVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SysBrandServiceImpl implements SysBrandService {

    private final SysBrandMapper brandMapper;

    @Override
    public BrandVO current() {
        Long brandId = UserContext.currentBrandId();
        SysBrand brand = brandMapper.selectById(brandId);
        if (brand == null) {
            throw new BizException(ResultCode.BRAND_NOT_FOUND);
        }
        return toVO(brand);
    }

    private BrandVO toVO(SysBrand b) {
        BrandVO vo = new BrandVO();
        vo.setId(b.getId());
        vo.setBrandCode(b.getBrandCode());
        vo.setBrandName(b.getBrandName());
        vo.setLogoUrl(b.getLogoUrl());
        vo.setContactPhone(b.getContactPhone());
        vo.setWalletDeductOrder(b.getWalletDeductOrder());
        vo.setRefundNeedAudit(b.getRefundNeedAudit());
        vo.setRefundDailyLimit(b.getRefundDailyLimit());
        vo.setRefundGiftConsumedAction(b.getRefundGiftConsumedAction());
        vo.setStatus(b.getStatus());
        vo.setCreateTime(b.getCreateTime());
        vo.setUpdateTime(b.getUpdateTime());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(BrandUpdateRequest request) {
        LoginUser current = UserContext.require();
        SysBrand brand = brandMapper.selectById(current.getBrandId());
        if (brand == null) {
            throw new BizException(ResultCode.BRAND_NOT_FOUND);
        }
        SysBrand patch = new SysBrand();
        patch.setId(brand.getId());
        patch.setBrandName(request.getBrandName());
        patch.setLogoUrl(request.getLogoUrl());
        patch.setContactPhone(request.getContactPhone());
        patch.setWalletDeductOrder(request.getWalletDeductOrder());
        patch.setRefundNeedAudit(request.getRefundNeedAudit());
        patch.setRefundDailyLimit(request.getRefundDailyLimit());
        patch.setRefundGiftConsumedAction(request.getRefundGiftConsumedAction());
        brandMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRefundRule(BrandUpdateRequest request) {
        LoginUser current = UserContext.require();
        SysBrand brand = brandMapper.selectById(current.getBrandId());
        if (brand == null) {
            throw new BizException(ResultCode.BRAND_NOT_FOUND);
        }
        if (request.getRefundDailyLimit() != null && request.getRefundDailyLimit() < 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "每日退卡次数不能为负");
        }
        if (request.getRefundNeedAudit() != null
                && request.getRefundNeedAudit() != 0 && request.getRefundNeedAudit() != 1) {
            throw new BizException(ResultCode.PARAM_INVALID, "审核开关只能是 0 或 1");
        }
        String action = request.getRefundGiftConsumedAction();
        if (action != null && !"REJECT".equals(action) && !"ALLOW".equals(action)) {
            throw new BizException(ResultCode.PARAM_INVALID, "赠送金处置只能是 REJECT 或 ALLOW");
        }

        SysBrand patch = new SysBrand();
        patch.setId(brand.getId());
        patch.setRefundNeedAudit(request.getRefundNeedAudit());
        patch.setRefundDailyLimit(request.getRefundDailyLimit());
        patch.setRefundGiftConsumedAction(action);
        brandMapper.updateById(patch);
    }}
