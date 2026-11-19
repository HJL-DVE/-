package com.teaclub.cloud.wallet.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 退款回冲命令。
 *
 * <p>回冲金额必须来自原交易的资金构成，由 {@code RefundService} 计算后传入，
 * 不允许外部直接指定"退多少钱"。
 */
@Data
@Builder
public class RefundInCmd {
    private Long brandId;
    private Long memberId;
    private Long storeId;
    /** 幂等键：退款单号 */
    private String refundNo;
    /** 退回实付余额（分） */
    private Long refundPrincipalAmount;
    /** 退回赠送余额（分） */
    private Long refundGiftAmount;
    private String remark;
    private Long operatorId;
    private String operatorType;
}
