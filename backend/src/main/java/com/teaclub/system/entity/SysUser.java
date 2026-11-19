package com.teaclub.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private String username;
    private String password;
    private String realName;
    private String phone;
    private String email;
    private String avatarUrl;
    private String userType;
    private String status;
    private Integer loginFailCount;
    private LocalDateTime lockUntil;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private LocalDateTime passwordUpdateTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
