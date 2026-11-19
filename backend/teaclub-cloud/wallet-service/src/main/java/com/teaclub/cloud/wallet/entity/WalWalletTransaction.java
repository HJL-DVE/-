package com.teaclub.cloud.wallet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 钱包流水。不可修改、不可删除。
 *
 * <p>唯一键 {@code uk_txn_biz(biz_type, biz_no)} 是资金幂等的最终防线：
 * 同一业务单号在同一业务类型下只能入账一次。
 */
@Data
@TableName("wal_wallet_transaction")
public class WalWalletTransaction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long walletId;
    private Long memberId;
    private Long storeId;
    private String txnNo;
    private String bizType;
    private String bizNo;
    /** 实付金变动额（分），正入负出 */
    private Long principalChange;
    private Long principalBefore;
    private Long principalAfter;
    /** 赠送金变动额（分） */
    private Long giftChange;
    private Long giftBefore;
    private Long giftAfter;
    private Long amountPaid;
    private Long giftAmount;
    private LocalDateTime giftExpireTime;
    private String remark;
    private Long operatorId;
    private String operatorType;
    private String traceId;
    private LocalDateTime createTime;
}
