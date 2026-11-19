package com.teaclub.system.service.impl;

import com.teaclub.common.api.ResultCode;
import com.teaclub.common.context.LoginUser;
import com.teaclub.common.context.UserContext;
import com.teaclub.common.exception.BizException;
import com.teaclub.system.dto.BrandUpdateRequest;
import com.teaclub.system.entity.SysBrand;
import com.teaclub.system.mapper.SysBrandMapper;
import com.teaclub.system.service.SysBrandService;
import com.teaclub.system.vo.BrandVO;
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
        vo.setWalletCrossStoreEnabled(b.getWalletCrossStoreEnabled());
        vo.setWalletDeductOrder(b.getWalletDeductOrder());
        vo.setGiftExpireDays(b.getGiftExpireDays());
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
        patch.setWalletCrossStoreEnabled(request.getWalletCrossStoreEnabled());
        patch.setWalletDeductOrder(request.getWalletDeductOrder());
        patch.setGiftExpireDays(request.getGiftExpireDays());
        brandMapper.updateById(patch);
    }
}
