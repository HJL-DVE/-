package com.teaclub.wallet.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "会员钱包")
public class WalletVO {
    private Long walletId;
    private Long memberId;
    private String memberNo;
    private String memberPhone;
    private String memberNickname;

    @Schema(description = "实付余额（分）")
    private Long principalBalance;
    @Schema(description = "赠送余额（分）")
    private Long giftBalance;
    @Schema(description = "总余额（分）")
    private Long totalBalance;

    private Long totalPrincipalIn;
    private Long totalGiftIn;
    private Long totalPrincipalOut;
    private Long totalGiftOut;
    private Long totalRefundIn;

    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
