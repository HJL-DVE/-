package com.teaclub.member.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("mem_member")
public class MemMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private String memberNo;
    private String phone;
    private String password;
    private String nickname;
    private String avatarUrl;
    private String gender;
    private LocalDate birthday;
    private Long levelId;
    private Long registerStoreId;
    private String registerChannel;
    private Long totalConsumeAmount;
    private Long totalRechargeAmount;
    private Integer totalOrderCount;
    private Long points;
    private LocalDateTime lastConsumeTime;
    private String status;
    private String freezeReason;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
