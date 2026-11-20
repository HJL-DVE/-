package com.teaclub.cloud.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.marketing.entity.MktPointsExchange;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MktPointsExchangeMapper extends BaseMapper<MktPointsExchange> {

    @Select("SELECT COUNT(*) FROM mkt_points_exchange WHERE goods_id = #{goodsId} AND member_id = #{memberId}")
    int countByMember(@Param("goodsId") Long goodsId, @Param("memberId") Long memberId);
}
