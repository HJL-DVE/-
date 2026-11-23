package com.teaclub.cloud.wallet.dto;

import lombok.Data;

/** 后台审核退卡申请。 */
@Data
public class CardRefundAuditRequest {

    /** APPROVED 通过 / REJECTED 驳回 */
    private String status;

    private String remark;

    /** 备注：审核通过时可指定退款单号，留空由系统生成 */
    private String refundNo;
}
