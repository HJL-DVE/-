package com.teaclub.cloud.wallet.service;

import com.teaclub.cloud.wallet.entity.WalWalletTransaction;
import com.teaclub.cloud.wallet.mapper.WalWalletTransactionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 赠送金到期核销任务。
 *
 * <p>充值入账时会在赠送流水上写入 gift_expire_time（充值时间 + 充值方案配置的
 * gift_expire_days）。本任务定期扫描已到期、尚未核销的充值流水，调用
 * {@link WalletService#expireGift(Long)} 把剩余赠送金扣回钱包。
 *
 * <p>幂等由 GIFT_EXPIRE 流水的 biz_no（GIFT-EXPIRE-{充值流水ID}）+ 唯一索引保证；
 * 核销金额 = min(该笔赠送额, 当前赠送余额)，不会把余额扣成负数。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GiftExpiryScheduler {

    private final WalWalletTransactionMapper txnMapper;
    private final WalletService walletService;

    @Scheduled(fixedDelayString = "${teaclub.gift.expiry-scan-ms:60000}")
    public void expireGift() {
        List<WalWalletTransaction> expired = txnMapper.findExpiredGiftRecharges(100);
        if (expired == null || expired.isEmpty()) {
            return;
        }
        for (WalWalletTransaction txn : expired) {
            try {
                walletService.expireGift(txn.getId());
            } catch (Exception e) {
                // 单笔失败不能中断整批；下次扫描会重试（幂等键保证不会重复扣款）
                log.error("赠送金到期核销失败 充值流水={} memberId={}", txn.getId(), txn.getMemberId(), e);
            }
        }
    }
}
