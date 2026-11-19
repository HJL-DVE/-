package com.teaclub.cloud.wallet.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 余额变动结果，包含变动构成与变动后余额。
 */
@Data
@Builder
public class WalletChangeResult {
    private Long walletId;
    private Long txnId;
    private String txnNo;

    private Long principalChange;
    private Long giftChange;
    private Long principalBefore;
    private Long principalAfter;
    private Long giftBefore;
    private Long giftAfter;

    /** 是否因为幂等命中而直接返回（未产生新流水） */
    private boolean idempotentHit;
}
