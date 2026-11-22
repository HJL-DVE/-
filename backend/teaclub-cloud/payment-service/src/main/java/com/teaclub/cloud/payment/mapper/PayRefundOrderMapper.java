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

    /**
     * 查询同一业务单当前有效的退款单（充值退卡的幂等入口）。
     *
     * <p>排除 FAILED/CANCELLED —— 渠道受理失败的退款允许重新发起。
     * 使用 FOR UPDATE：在 idx_refund_biz 上加间隙锁，防止两个请求同时为同一充值单建退款单。
     */
    @Select("SELECT * FROM pay_refund_order WHERE biz_type = #{bizType} AND biz_no = #{bizNo} "
            + "AND status NOT IN ('FAILED','CANCELLED') ORDER BY id DESC LIMIT 1 FOR UPDATE")
    PayRefundOrder selectActiveByBizForUpdate(@Param("bizType") String bizType,
                                              @Param("bizNo") String bizNo);
}
