package com.teaclub.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.order.entity.OmsOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface OmsOrderMapper extends BaseMapper<OmsOrder> {

    @Select("SELECT * FROM oms_order WHERE order_no = #{orderNo} AND deleted = 0 FOR UPDATE")
    OmsOrder selectByNoForUpdate(@Param("orderNo") String orderNo);

    @Select("SELECT * FROM oms_order WHERE order_no = #{orderNo} AND deleted = 0")
    OmsOrder selectByNo(@Param("orderNo") String orderNo);

    /** 门店营业额统计 */
    @Select("""
            SELECT store_id AS storeId,
                   COUNT(*) AS orderCount,
                   COALESCE(SUM(paid_amount), 0) AS paidAmount,
                   COALESCE(SUM(refunded_amount), 0) AS refundedAmount,
                   COALESCE(SUM(paid_amount - refunded_amount), 0) AS netAmount
            FROM oms_order
            WHERE brand_id = #{brandId}
              AND deleted = 0
              AND status IN ('PAID','COMPLETED','REFUNDING')
              AND pay_time >= #{start} AND pay_time < #{end}
            GROUP BY store_id
            """)
    List<Map<String, Object>> statByStore(@Param("brandId") Long brandId,
                                          @Param("start") java.time.LocalDateTime start,
                                          @Param("end") java.time.LocalDateTime end);
}
