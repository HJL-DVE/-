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
}
