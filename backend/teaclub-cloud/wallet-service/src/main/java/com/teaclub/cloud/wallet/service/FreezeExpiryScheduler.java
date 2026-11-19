package com.teaclub.cloud.wallet.service;

import com.teaclub.cloud.wallet.entity.WalWalletFreeze;
import com.teaclub.cloud.wallet.mapper.WalWalletFreezeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 冻结超时自动释放任务。
 *
 * <p>解决规格第五区「消费确认超时」与第四区「失败订单释放冻结」：
 * 订单在冻结后若因宕机/超时未确认扣款，冻结记录会在 expire_time 后
 * 被自动释放，避免资金被永久占用。
 *
 * <p>释放冻结<b>不写资金流水</b>（钱没动），只把冻结记录改为 RELEASED，
 * 因此不破坏「余额 = 流水累计」不变量。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FreezeExpiryScheduler {

    private final WalWalletFreezeMapper freezeMapper;

    @Scheduled(fixedDelayString = "${teaclub.freeze.expiry-scan-ms:10000}")
    public void releaseExpiredFreezes() {
        List<WalWalletFreeze> expired = freezeMapper.findExpired();
        for (WalWalletFreeze f : expired) {
            // 用条件更新防止与手动释放/确认并发冲突
            int n = freezeMapper.releaseExpired(f.getId());
            if (n > 0) {
                log.warn("冻结超时自动释放: orderNo={} freezeId={} 实付={} 赠送={}",
                        f.getOrderNo(), f.getId(), f.getFreezePrincipal(), f.getFreezeGift());
            }
        }
    }
}
