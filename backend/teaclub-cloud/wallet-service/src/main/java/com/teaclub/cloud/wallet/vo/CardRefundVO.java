package com.teaclub.cloud.wallet.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 退卡申请详情（会员端与后台共用）。 */
@Data
public class CardRefundVO {
    private Long id;
    private String refundNo;
    private Long memberId;
    private String memberNo;
    private String memberNickname;
    private Long principalAmount;
    private Long giftVoidAmount;
    private String status;
    private String applyReason;
    private Long auditBy;
    private LocalDateTime auditTime;
    private String auditRemark;
    private String refundTxnNo;
    private LocalDateTime createTime;
}
