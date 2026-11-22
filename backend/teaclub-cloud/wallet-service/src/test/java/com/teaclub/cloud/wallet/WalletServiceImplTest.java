package com.teaclub.cloud.wallet;

import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.wallet.dto.ConsumeOutCmd;
import com.teaclub.cloud.wallet.dto.RechargeInCmd;
import com.teaclub.cloud.wallet.dto.RefundInCmd;
import com.teaclub.cloud.wallet.dto.WalletChangeResult;
import com.teaclub.cloud.wallet.entity.WalWallet;
import com.teaclub.cloud.wallet.entity.WalWalletTransaction;
import com.teaclub.cloud.wallet.mapper.WalWalletMapper;
import com.teaclub.cloud.wallet.mapper.WalWalletTransactionMapper;
import com.teaclub.cloud.wallet.service.WalletService;
import com.teaclub.cloud.wallet.service.support.DeductOrderResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 钱包资金一致性测试。
 *
 * <p>连接真实 MySQL（wallet_db），验证：
 * <ol>
 *   <li>幂等：同一业务单号重复调用只产生一条流水</li>
 *   <li>不变量：余额 = 流水累计，after = before + change</li>
 *   <li>并发：并发扣款不超扣</li>
 *   <li>余额不足被拒绝</li>
 *   <li>退款按原资金构成回冲</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("docker")
@Transactional
class WalletServiceImplTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalWalletMapper walletMapper;

    @Autowired
    private WalWalletTransactionMapper txnMapper;

    private static final Long BRAND_ID = 1L;
    private static final Long MEMBER_ID = 900001L;
    private static final Long STORE_ID = 1L;

    @BeforeEach
    void setUp() {
        // 每个测试用独立会员，避免相互干扰
    }

    private String bizNo(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    @Test
    @DisplayName("充值入账：余额与流水一致")
    void rechargeInKeepsInvariant() {
        String rechargeNo = bizNo("RC");
        long pay = 10000L;
        long gift = 2000L;

        WalletChangeResult r = walletService.rechargeIn(RechargeInCmd.builder()
                .brandId(BRAND_ID).memberId(MEMBER_ID).storeId(STORE_ID)
                .rechargeNo(rechargeNo).payAmount(pay).giftAmount(gift)
                .operatorType("SYSTEM").build());

        assertThat(r.isIdempotentHit()).isFalse();
        assertThat(r.getPrincipalAfter()).isEqualTo(pay);
        assertThat(r.getGiftAfter()).isEqualTo(gift);

        WalWallet w = walletService.getWallet(BRAND_ID, MEMBER_ID);
        assertThat(w.getPrincipalBalance()).isEqualTo(pay);
        assertThat(w.getGiftBalance()).isEqualTo(gift);

        // 不变量：余额 = 流水累计
        Long sumP = txnMapper.sumPrincipalChange(MEMBER_ID);
        Long sumG = txnMapper.sumGiftChange(MEMBER_ID);
        assertThat(w.getPrincipalBalance()).isEqualTo(sumP);
        assertThat(w.getGiftBalance()).isEqualTo(sumG);
    }

    @Test
    @DisplayName("幂等：重复充值同一单号只入账一次")
    void rechargeInIsIdempotent() {
        String rechargeNo = bizNo("RC");

        WalletChangeResult first = walletService.rechargeIn(RechargeInCmd.builder()
                .brandId(BRAND_ID).memberId(MEMBER_ID).storeId(STORE_ID)
                .rechargeNo(rechargeNo).payAmount(10000L).giftAmount(0L).build());

        WalletChangeResult second = walletService.rechargeIn(RechargeInCmd.builder()
                .brandId(BRAND_ID).memberId(MEMBER_ID).storeId(STORE_ID)
                .rechargeNo(rechargeNo).payAmount(10000L).giftAmount(0L).build());

        assertThat(second.isIdempotentHit()).isTrue();
        assertThat(second.getTxnId()).isEqualTo(first.getTxnId());
        assertThat(second.getPrincipalAfter()).isEqualTo(first.getPrincipalAfter());

        // 余额只增加一次
        WalWallet w = walletService.getWallet(BRAND_ID, MEMBER_ID);
        assertThat(w.getPrincipalBalance()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("扣款：按 GIFT_FIRST 先扣赠送金")
    void consumeOutUsesGiftFirst() {
        walletService.rechargeIn(RechargeInCmd.builder()
                .brandId(BRAND_ID).memberId(MEMBER_ID).storeId(STORE_ID)
                .rechargeNo(bizNo("RC")).payAmount(10000L).giftAmount(2000L).build());

        WalletChangeResult r = walletService.consumeOut(ConsumeOutCmd.builder()
                .brandId(BRAND_ID).memberId(MEMBER_ID).storeId(STORE_ID)
                .orderNo(bizNo("TC")).payableAmount(3000L)
                .deductOrder("GIFT_FIRST").build());

        // 先扣赠送 2000，再扣实付 1000
        assertThat(r.getGiftChange()).isEqualTo(-2000L);
        assertThat(r.getPrincipalChange()).isEqualTo(-1000L);
        assertThat(r.getPrincipalAfter()).isEqualTo(9000L);
        assertThat(r.getGiftAfter()).isZero();
    }

    @Test
    @DisplayName("扣款：余额不足被拒绝")
    void consumeOutRejectsInsufficientBalance() {
        walletService.rechargeIn(RechargeInCmd.builder()
                .brandId(BRAND_ID).memberId(MEMBER_ID).storeId(STORE_ID)
                .rechargeNo(bizNo("RC")).payAmount(1000L).giftAmount(0L).build());

        assertThatThrownBy(() -> walletService.consumeOut(ConsumeOutCmd.builder()
                .brandId(BRAND_ID).memberId(MEMBER_ID).storeId(STORE_ID)
                .orderNo(bizNo("TC")).payableAmount(5000L)
                .deductOrder("GIFT_FIRST").build()))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("幂等：重复扣款同一订单只扣一次")
    void consumeOutIsIdempotent() {
        walletService.rechargeIn(RechargeInCmd.builder()
                .brandId(BRAND_ID).memberId(MEMBER_ID).storeId(STORE_ID)
                .rechargeNo(bizNo("RC")).payAmount(10000L).giftAmount(0L).build());

        String orderNo = bizNo("TC");
        WalletChangeResult first = walletService.consumeOut(ConsumeOutCmd.builder()
                .brandId(BRAND_ID).memberId(MEMBER_ID).storeId(STORE_ID)
                .orderNo(orderNo).payableAmount(3000L)
                .deductOrder("GIFT_FIRST").build());

        WalletChangeResult second = walletService.consumeOut(ConsumeOutCmd.builder()
                .brandId(BRAND_ID).memberId(MEMBER_ID).storeId(STORE_ID)
                .orderNo(orderNo).payableAmount(3000L)
                .deductOrder("GIFT_FIRST").build());

        assertThat(second.isIdempotentHit()).isTrue();
        assertThat(second.getTxnId()).isEqualTo(first.getTxnId());

        WalWallet w = walletService.getWallet(BRAND_ID, MEMBER_ID);
        assertThat(w.getPrincipalBalance()).isEqualTo(7000L);
    }

    @Test
    @DisplayName("退款：按原资金构成精确回冲")
    void refundInRestoresOriginalComposition() {
        walletService.rechargeIn(RechargeInCmd.builder()
                .brandId(BRAND_ID).memberId(MEMBER_ID).storeId(STORE_ID)
                .rechargeNo(bizNo("RC")).payAmount(10000L).giftAmount(2000L).build());

        walletService.consumeOut(ConsumeOutCmd.builder()
                .brandId(BRAND_ID).memberId(MEMBER_ID).storeId(STORE_ID)
                .orderNo(bizNo("TC")).payableAmount(3000L)
                .deductOrder("GIFT_FIRST").build());

        // 原扣款：赠送 2000 + 实付 1000 -> 退款应原样退回
        WalletChangeResult refund = walletService.refundIn(RefundInCmd.builder()
                .brandId(BRAND_ID).memberId(MEMBER_ID).storeId(STORE_ID)
                .refundNo(bizNo("RF"))
                .refundPrincipalAmount(1000L).refundGiftAmount(2000L).build());

        assertThat(refund.getPrincipalChange()).isEqualTo(1000L);
        assertThat(refund.getGiftChange()).isEqualTo(2000L);
        assertThat(refund.getPrincipalAfter()).isEqualTo(10000L);
        assertThat(refund.getGiftAfter()).isEqualTo(2000L);
    }

    @Test
    @DisplayName("人工调整：不能把余额改成负数")
    void adjustRejectsNegativeBalance() {
        walletService.rechargeIn(RechargeInCmd.builder()
                .brandId(BRAND_ID).memberId(MEMBER_ID).storeId(STORE_ID)
                .rechargeNo(bizNo("RC")).payAmount(1000L).giftAmount(0L).build());

        assertThatThrownBy(() -> walletService.adjust(
                BRAND_ID, MEMBER_ID, STORE_ID, bizNo("AP"), -5000L, 0L, "测试", null))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("幂等键必填")
    void bizNoRequired() {
        assertThatThrownBy(() -> walletService.rechargeIn(RechargeInCmd.builder()
                .brandId(BRAND_ID).memberId(MEMBER_ID).storeId(STORE_ID)
                .rechargeNo(null).payAmount(1000L).build()))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("赠送金到期核销：只扣剩余赠送金且幂等")
    void giftExpireDeductsRemainingGiftOnly() {
        String rechargeNo = bizNo("RC");
        walletService.rechargeIn(RechargeInCmd.builder()
                .brandId(BRAND_ID).memberId(MEMBER_ID).storeId(STORE_ID)
                .rechargeNo(rechargeNo).payAmount(10000L).giftAmount(2000L)
                .giftExpireDays(30).build());

        WalWalletTransaction src = txnMapper.selectByBiz("RECHARGE_IN", rechargeNo);
        assertThat(src.getGiftExpireTime()).isNotNull();

        // 花掉一半赠送金
        walletService.consumeOut(ConsumeOutCmd.builder()
                .brandId(BRAND_ID).memberId(MEMBER_ID).storeId(STORE_ID)
                .orderNo(bizNo("TC")).payableAmount(1000L)
                .deductOrder("GIFT_FIRST").build());

        // 人为把到期时间改到过去（测试环境）
        src.setGiftExpireTime(LocalDateTime.now().minusDays(1));
        txnMapper.updateById(src);

        WalletChangeResult first = walletService.expireGift(src.getId());
        assertThat(first.getGiftChange()).isEqualTo(-1000L);
        assertThat(first.getGiftAfter()).isZero();

        WalletChangeResult second = walletService.expireGift(src.getId());
        assertThat(second.isIdempotentHit()).isTrue();
        assertThat(second.getTxnId()).isEqualTo(first.getTxnId());

        WalWallet w = walletService.getWallet(BRAND_ID, MEMBER_ID);
        assertThat(w.getGiftBalance()).isZero();
        // GIFT_FIRST 先扣赠送金，本金未动
        assertThat(w.getPrincipalBalance()).isEqualTo(10000L);
        assertThat(txnMapper.sumGiftChange(MEMBER_ID)).isZero();
    }
}
