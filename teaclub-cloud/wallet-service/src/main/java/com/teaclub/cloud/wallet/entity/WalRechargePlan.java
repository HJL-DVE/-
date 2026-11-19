package com.teaclub.cloud.wallet.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wal_recharge_plan")
public class WalRechargePlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private String planCode;
    private String planName;
    private Long rechargeAmount;
    private Long giftAmount;
    private Long giftCouponTemplateId;
    private Long giftPoints;
    private Integer giftExpireDays;
    private Long minLevelId;
    private Integer customAmountEnabled;
    private Long minCustomAmount;
    private Long maxCustomAmount;
    private Integer perMemberLimit;
    private Integer sort;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
