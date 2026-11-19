package com.teaclub.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.payment.entity.PayPaymentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PayPaymentOrderMapper extends BaseMapper<PayPaymentOrder> {

    @Select("SELECT * FROM pay_payment_order WHERE payment_no = #{paymentNo} FOR UPDATE")
    PayPaymentOrder selectByNoForUpdate(@Param("paymentNo") String paymentNo);

    @Select("SELECT * FROM pay_payment_order WHERE payment_no = #{paymentNo}")
    PayPaymentOrder selectByNo(@Param("paymentNo") String paymentNo);

    @Select("SELECT * FROM pay_payment_order WHERE biz_type = #{bizType} AND biz_no = #{bizNo} FOR UPDATE")
    PayPaymentOrder selectByBizForUpdate(@Param("bizType") String bizType, @Param("bizNo") String bizNo);
}
