package com.teaclub.cloud.wallet.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 钱包冻结记录。
 *
 * <p><b>冻结不修改账户余额</b>，只在此表记录冻结金额。
 * 可用余额 = 账户余额 - SUM(状态=FROZEN 的冻结金额)。
 *
 * <p>只有「确认扣款」才真正调用 WalletService.consumeOut 写资金流水，
 * 「释放冻结」仅把本记录状态改为 RELEASED，同样不写流水（钱没动）。
 */
@Data
@TableName("wal_wallet_freeze")
public class WalWalletFreeze {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long brandId;

    private Long walletId;

    private Long memberId;

    /** 业务单号（冻结幂等键：同一订单只能冻结一次） */
    private String orderNo;

    /** 冻结实付余额（分） */
    private Long freezePrincipal;

    /** 冻结赠送余额（分） */
    private Long freezeGift;

    /** FROZEN / CONFIRMED / RELEASED */
    private String status;

    /** 确认扣款时生成的资金流水号 */
    private String confirmedTxnNo;

    /** 冻结过期时间（超时自动释放） */
    private LocalDateTime expireTime;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
