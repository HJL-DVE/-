package com.teaclub.cloud.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.payment.entity.PayPaymentOrder;
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

    /**
     * 查询超时未完成的支付单（供关单任务使用）。
     *
     * <p>只扫 CREATED / PAYING —— SUCCESS / FAILED / CLOSED 都是终态。
     */
    @org.apache.ibatis.annotations.Select(
            "SELECT * FROM pay_payment_order "
            + "WHERE status IN ('CREATED','PAYING') "
            + "AND create_time < DATE_SUB(NOW(3), INTERVAL #{minutes} MINUTE) "
            + "ORDER BY id LIMIT #{limit}")
    java.util.List<PayPaymentOrder> findTimeout(
            @org.apache.ibatis.annotations.Param("minutes") int minutes,
            @org.apache.ibatis.annotations.Param("limit") int limit);
}
