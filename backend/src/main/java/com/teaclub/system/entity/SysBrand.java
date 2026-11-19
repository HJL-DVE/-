package com.teaclub.system.entity;

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
    private Integer walletCrossStoreEnabled;
    private String walletDeductOrder;
    private Integer giftExpireDays;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
