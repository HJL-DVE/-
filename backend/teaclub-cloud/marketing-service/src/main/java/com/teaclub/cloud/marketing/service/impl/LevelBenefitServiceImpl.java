package com.teaclub.cloud.marketing.service.impl;

import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.marketing.entity.MktLevelBenefit;
import com.teaclub.cloud.marketing.enums.BenefitCode;
import com.teaclub.cloud.marketing.mapper.MktLevelBenefitMapper;
import com.teaclub.cloud.marketing.service.LevelBenefitService;
import com.teaclub.cloud.security.context.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LevelBenefitServiceImpl implements LevelBenefitService {

    private final MktLevelBenefitMapper benefitMapper;

    @Override
    public List<MktLevelBenefit> listByLevel(Long levelId) {
        return benefitMapper.selectByLevel(brandId(), levelId);
    }

    @Override
    public Map<Long, List<MktLevelBenefit>> listAll() {
        var w = com.baomidou.mybatisplus.core.toolkit.Wrappers.<MktLevelBenefit>lambdaQuery()
                .eq(MktLevelBenefit::getBrandId, brandId())
                .orderByAsc(MktLevelBenefit::getLevelId);
        Map<Long, List<MktLevelBenefit>> out = new LinkedHashMap<>();
        for (MktLevelBenefit b : benefitMapper.selectList(w)) {
            out.computeIfAbsent(b.getLevelId(), k -> new ArrayList<>()).add(b);
        }
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(MktLevelBenefit benefit) {
        if (benefit.getLevelId() == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "等级 ID 不能为空");
        }
        try {
            BenefitCode.valueOf(benefit.getBenefitCode());
        } catch (Exception e) {
            throw new BizException(ResultCode.PARAM_INVALID,
                    "不支持的权益编码: " + benefit.getBenefitCode());
        }
        Long brand = brandId();
        MktLevelBenefit exist = benefitMapper.selectOne(brand, benefit.getLevelId(), benefit.getBenefitCode());
        if (exist != null) {
            // 同等级同编码唯一：更新而不是新增，避免重复配置
            MktLevelBenefit patch = new MktLevelBenefit();
            patch.setId(exist.getId());
            patch.setBenefitName(benefit.getBenefitName());
            patch.setBenefitValue(benefit.getBenefitValue());
            patch.setStatus(benefit.getStatus() == null ? "ENABLED" : benefit.getStatus());
            benefitMapper.updateById(patch);
            log.info("[LEVEL-BENEFIT] 更新 id={} level={} code={}",
                    exist.getId(), benefit.getLevelId(), benefit.getBenefitCode());
            return exist.getId();
        }
        benefit.setBrandId(brand);
        if (benefit.getStatus() == null) {
            benefit.setStatus("ENABLED");
        }
        benefitMapper.insert(benefit);
        log.info("[LEVEL-BENEFIT] 新增 id={} level={} code={}",
                benefit.getId(), benefit.getLevelId(), benefit.getBenefitCode());
        return benefit.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MktLevelBenefit exist = benefitMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "权益不存在");
        }
        benefitMapper.deleteById(id);
    }

    @Override
    public int pointRateOf(Long brandId, Long levelId) {
        if (levelId == null) {
            return 100;
        }
        MktLevelBenefit b = benefitMapper.selectOne(brandId, levelId, BenefitCode.POINT_RATE.name());
        if (b == null || b.getBenefitValue() == null) {
            return 100;
        }
        try {
            int rate = Integer.parseInt(b.getBenefitValue().trim());
            return rate <= 0 ? 100 : rate;
        } catch (NumberFormatException e) {
            log.warn("[LEVEL-BENEFIT] 积分倍率配置非法 level={} value={}", levelId, b.getBenefitValue());
            return 100;
        }
    }

    @Override
    public List<Long> birthdayCouponTemplates(Long brandId, Long levelId) {
        MktLevelBenefit b = benefitMapper.selectOne(brandId, levelId, BenefitCode.BIRTHDAY_COUPON.name());
        List<Long> out = new ArrayList<>();
        if (b == null || b.getBenefitValue() == null) {
            return out;
        }
        for (String s : b.getBenefitValue().split(",")) {
            try {
                out.add(Long.parseLong(s.trim()));
            } catch (NumberFormatException ignore) {
                // 忽略非法项
            }
        }
        return out;
    }

    private Long brandId() {
        Long b = UserContext.currentBrandIdOrNull();
        return b == null ? 1L : b;
    }
}
