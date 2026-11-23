package com.teaclub.cloud.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_brand")
public class SysBrand {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String brandCode;
    private String brandName;
    private String logoUrl;
    private String contactPhone;
    private String walletDeductOrder;
    /** 退款是否需要人工审核：1 需要 0 自助秒退 */
    private Integer refundNeedAudit;
    /** 同一会员每日退卡次数上限，0 表示不限 */
    private Integer refundDailyLimit;
    /** 赠送金已被消费时的处置：REJECT 拒绝转人工 / ALLOW 按剩余扣回 */
    private String refundGiftConsumedAction;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
