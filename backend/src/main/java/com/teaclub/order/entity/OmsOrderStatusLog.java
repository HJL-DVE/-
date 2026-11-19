package com.teaclub.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("oms_order_status_log")
public class OmsOrderStatusLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long orderId;
    private String orderNo;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String operatorType;
    private String remark;
    private LocalDateTime createTime;
}
