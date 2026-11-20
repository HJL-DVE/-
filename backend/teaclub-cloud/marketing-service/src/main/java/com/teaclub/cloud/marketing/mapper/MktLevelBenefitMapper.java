package com.teaclub.cloud.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.marketing.entity.MktLevelBenefit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MktLevelBenefitMapper extends BaseMapper<MktLevelBenefit> {

    @Select("""
            SELECT * FROM mkt_level_benefit
            WHERE brand_id = #{brandId} AND level_id = #{levelId} AND status = 'ENABLED'
            """)
    List<MktLevelBenefit> selectByLevel(@Param("brandId") Long brandId, @Param("levelId") Long levelId);

    @Select("""
            SELECT * FROM mkt_level_benefit
            WHERE brand_id = #{brandId} AND level_id = #{levelId} AND benefit_code = #{code}
            LIMIT 1
            """)
    MktLevelBenefit selectOne(@Param("brandId") Long brandId,
                              @Param("levelId") Long levelId,
                              @Param("code") String code);
}
