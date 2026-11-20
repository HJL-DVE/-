package com.teaclub.cloud.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.marketing.entity.MktPointsRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MktPointsRuleMapper extends BaseMapper<MktPointsRule> {

    @Select("SELECT * FROM mkt_points_rule WHERE brand_id = #{brandId} AND rule_code = #{ruleCode} AND deleted = 0 LIMIT 1")
    MktPointsRule selectByCode(@Param("brandId") Long brandId, @Param("ruleCode") String ruleCode);
}
