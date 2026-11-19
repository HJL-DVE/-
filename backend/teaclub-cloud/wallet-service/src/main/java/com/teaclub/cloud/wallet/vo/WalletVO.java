package com.teaclub.cloud.wallet.vo;

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

    /** 契约别名（docs/33 §2.7）：与 walletId / memberNickname / memberPhone 同值，供管理端前端直接使用 */
    private Long id;
    private String memberName;
    private String phone;

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
