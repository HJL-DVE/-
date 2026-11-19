package com.teaclub.wallet.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "充值订单")
public class RechargeOrderVO {
    private Long id;
    private String rechargeNo;
    private Long memberId;
    private String memberName;
    private String memberPhone;
    private Long storeId;
    private String storeName;
    private Long planId;
    private String planName;

    @Schema(description = "充值金额（分）")
    private Long rechargeAmount;
    @Schema(description = "赠送金额（分）")
    private Long giftAmount;

    private String payChannel;
    private String status;
    private String paymentNo;
    private LocalDateTime payTime;
    private LocalDateTime createTime;
    private String remark;
}
