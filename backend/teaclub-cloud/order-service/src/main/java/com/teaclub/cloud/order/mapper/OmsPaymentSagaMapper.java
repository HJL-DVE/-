package com.teaclub.cloud.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.order.entity.OmsPaymentSaga;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OmsPaymentSagaMapper extends BaseMapper<OmsPaymentSaga> {

    @Select("SELECT * FROM oms_payment_saga WHERE order_no = #{orderNo}")
    OmsPaymentSaga selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 查询需要补偿的 Saga：仍处于 RUNNING 且超过指定秒数未推进。
     *
     * <p>典型场景：扣款成功后进程崩溃，订单未更新 —— 必须回冲资金。
     */
    @Select("SELECT * FROM oms_payment_saga WHERE status = 'RUNNING' "
            + "AND step IN ('DEDUCTING','DEDUCTED') "
            + "AND update_time < DATE_SUB(NOW(3), INTERVAL #{seconds} SECOND)")
    List<OmsPaymentSaga> findStale(@Param("seconds") int seconds);
}
