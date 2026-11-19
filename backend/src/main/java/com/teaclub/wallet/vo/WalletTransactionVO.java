package com.teaclub.wallet.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "钱包流水")
public class WalletTransactionVO {
    private Long id;
    private String txnNo;
    private Long memberId;
    private String memberName;
    private Long storeId;
    private String storeName;
    private String bizType;
    private String bizTypeName;
    private String bizNo;

    @Schema(description = "实付金变动（分）")
    private Long principalChange;
    private Long principalBefore;
    private Long principalAfter;
    @Schema(description = "赠送金变动（分）")
    private Long giftChange;
    private Long giftBefore;
    private Long giftAfter;

    private Long amountPaid;
    private Long giftAmount;
    private LocalDateTime giftExpireTime;
    private String remark;
    private String operatorType;
    private LocalDateTime createTime;
}
