package com.teaclub.wallet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wal_recharge_plan_store")
public class WalRechargePlanStore {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private Long storeId;
    private LocalDateTime createTime;
}
