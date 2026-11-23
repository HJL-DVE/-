package com.teaclub.cloud.wallet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/** 会员退卡申请（储值卡注销退余额）。 */
@Data
@TableName("wal_card_refund")
public class WalCardRefund {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long memberId;
    private String refundNo;
    /** 申请退还的实付本金（分） */
    private Long principalAmount;
    /** 作废的赠送金额（分），不折现 */
    private Long giftVoidAmount;
    private String status;
    private String applyReason;
    private Long auditBy;
    private LocalDateTime auditTime;
    private String auditRemark;
    private String refundTxnNo;
    @Version
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
