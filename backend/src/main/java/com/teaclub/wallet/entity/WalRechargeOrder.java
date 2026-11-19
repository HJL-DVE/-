package com.teaclub.wallet.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wal_recharge_order")
public class WalRechargeOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private String rechargeNo;
    private Long memberId;
    private Long storeId;
    private Long planId;
    private Long rechargeAmount;
    private Long giftAmount;
    private Integer giftExpireDays;
    private String payChannel;
    private String status;
    private String paymentNo;
    private LocalDateTime payTime;
    private LocalDateTime cancelTime;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
