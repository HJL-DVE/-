package com.teaclub.cloud.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user_store")
public class SysUserStore {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long storeId;
    private Integer isDefault;
    private LocalDateTime createTime;
}
