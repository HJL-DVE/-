package com.teaclub.cloud.member.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mem_member_level")
public class MemMemberLevel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private String levelCode;
    private String levelName;
    private Integer levelNo;
    private String upgradeType;
    private Long upgradeThreshold;
    private Integer discountRate;
    private Integer pointRate;
    private String iconUrl;
    private String benefits;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
