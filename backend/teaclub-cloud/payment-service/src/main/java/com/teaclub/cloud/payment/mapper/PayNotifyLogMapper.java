package com.teaclub.cloud.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.payment.entity.PayNotifyLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PayNotifyLogMapper extends BaseMapper<PayNotifyLog> {

    @Select("SELECT * FROM pay_notify_log WHERE provider = #{provider} AND notify_id = #{notifyId}")
    PayNotifyLog selectByNotifyId(@Param("provider") String provider, @Param("notifyId") String notifyId);
}
