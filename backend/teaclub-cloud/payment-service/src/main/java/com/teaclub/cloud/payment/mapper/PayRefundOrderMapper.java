package com.teaclub.cloud.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.payment.entity.PayRefundOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PayRefundOrderMapper extends BaseMapper<PayRefundOrder> {

    @Select("SELECT * FROM pay_refund_order WHERE refund_no = #{refundNo} FOR UPDATE")
    PayRefundOrder selectByNoForUpdate(@Param("refundNo") String refundNo);

    @Select("SELECT * FROM pay_refund_order WHERE payment_no = #{paymentNo} AND status IN ('SUCCESS','PROCESSING')")
    List<PayRefundOrder> selectActiveByPayment(@Param("paymentNo") String paymentNo);

    @Select("SELECT COALESCE(SUM(refund_amount),0) FROM pay_refund_order WHERE payment_no = #{paymentNo} AND status IN ('SUCCESS','PROCESSING')")
    Long sumRefundedByPayment(@Param("paymentNo") String paymentNo);

    /**
     * 按业务单号（订单号）汇总已占用退款额。
     *
     * <p>这是可退金额判定的权威来源 —— 位于 payment_db 本地，
     * 与退款单插入在同一本地事务内，不存在跨库一致性问题。
     * 排除 FAILED/CANCELLED，使失败退款可重新申请。
     */
    @Select("SELECT COALESCE(SUM(refund_amount),0) FROM pay_refund_order "
            + "WHERE biz_no = #{bizNo} AND biz_type = 'ORDER' "
            + "AND status NOT IN ('FAILED','CANCELLED')")
    Long sumReservedByBizNo(@Param("bizNo") String bizNo);
}
