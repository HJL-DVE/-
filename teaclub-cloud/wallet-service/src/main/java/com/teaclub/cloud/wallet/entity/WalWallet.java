package com.teaclub.cloud.wallet.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员钱包（品牌级，双余额）。
 *
 * <p>余额字段<b>只允许</b>通过 {@code WalletService} 的事务方法修改，
 * 禁止任何直接 UPDATE，否则破坏"余额 = 流水累计"不变量。
 */
@Data
@TableName("wal_wallet")
public class WalWallet {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long memberId;
    /** 实付余额（分） */
    private Long principalBalance;
    /** 赠送余额（分） */
    private Long giftBalance;
    private Long totalPrincipalIn;
    private Long totalGiftIn;
    private Long totalPrincipalOut;
    private Long totalGiftOut;
    private Long totalRefundIn;
    @Version
    private Integer version;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
