package com.teaclub.cloud.wallet.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "钱包统计（仪表盘）")
public class WalletStatsVO {

    @Schema(description = "钱包账户数")
    private Long walletCount;
    @Schema(description = "实付余额合计（分）")
    private Long principalTotal;
    @Schema(description = "赠送余额合计（分）")
    private Long giftTotal;
    @Schema(description = "总余额合计（分）")
    private Long totalBalance;
    @Schema(description = "累计充值实付合计（分，仅 PAID 充值单）")
    private Long rechargeTotal;
    @Schema(description = "累计赠送合计（分，仅 PAID 充值单）")
    private Long giftGrantTotal;
}
