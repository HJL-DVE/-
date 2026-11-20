package com.teaclub.cloud.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.marketing.entity.MktPointsGoods;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MktPointsGoodsMapper extends BaseMapper<MktPointsGoods> {

    /**
     * 扣减库存。
     *
     * <p>{@code stock = -1} 表示不限量；否则必须 {@code stock >= qty}，并发下不会超卖。
     */
    @Update("""
            UPDATE mkt_points_goods
            SET stock = stock - #{qty}, update_time = NOW(3)
            WHERE id = #{id} AND deleted = 0 AND status = 'ENABLED'
              AND (stock = -1 OR stock >= #{qty})
            """)
    int tryDeductStock(@Param("id") Long id, @Param("qty") int qty);
}
