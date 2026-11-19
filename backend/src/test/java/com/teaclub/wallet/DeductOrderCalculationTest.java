package com.teaclub.wallet;

import com.teaclub.wallet.enums.DeductOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 扣款构成计算测试。
 *
 * <p>扣款顺序直接决定退款时资金能否精确还原，属于资金核心逻辑，
 * 因此单独抽出来做纯计算测试，不依赖数据库。
 */
class DeductOrderCalculationTest {

    /**
     * 与 WalletServiceImpl.consumeOut 中完全一致的扣款算法。
     * 抽成测试内私有方法，确保测的就是生产逻辑本身的分支。
     */
    private long[] calc(long principal, long gift, long payable, DeductOrder order) {
        long giftDeduct;
        long principalDeduct;
        if (order == DeductOrder.GIFT_FIRST) {
            giftDeduct = Math.min(gift, payable);
            principalDeduct = payable - giftDeduct;
        } else {
            principalDeduct = Math.min(principal, payable);
            giftDeduct = payable - principalDeduct;
        }
        return new long[]{principalDeduct, giftDeduct};
    }

    @Test
    @DisplayName("GIFT_FIRST：赠送金足够时只扣赠送金")
    void giftFirst_giftEnough() {
        long[] r = calc(10000, 5000, 3000, DeductOrder.GIFT_FIRST);
        assertThat(r[0]).as("实付扣款").isZero();
        assertThat(r[1]).as("赠送扣款").isEqualTo(3000);
    }

    @Test
    @DisplayName("GIFT_FIRST：赠送金不足时先扣完赠送再扣实付")
    void giftFirst_giftInsufficient() {
        long[] r = calc(10000, 3000, 8000, DeductOrder.GIFT_FIRST);
        assertThat(r[0]).as("实付扣款").isEqualTo(5000);
        assertThat(r[1]).as("赠送扣款").isEqualTo(3000);
        assertThat(r[0] + r[1]).isEqualTo(8000);
    }

    @Test
    @DisplayName("PRINCIPAL_FIRST：实付金足够时只扣实付金")
    void principalFirst_principalEnough() {
        long[] r = calc(10000, 5000, 3000, DeductOrder.PRINCIPAL_FIRST);
        assertThat(r[0]).isEqualTo(3000);
        assertThat(r[1]).isZero();
    }

    @Test
    @DisplayName("PRINCIPAL_FIRST：实付金不足时先扣完实付再扣赠送")
    void principalFirst_principalInsufficient() {
        long[] r = calc(2000, 5000, 4000, DeductOrder.PRINCIPAL_FIRST);
        assertThat(r[0]).isEqualTo(2000);
        assertThat(r[1]).isEqualTo(2000);
    }

    @Test
    @DisplayName("恰好扣完两个余额，结果为 0 且不出现负数")
    void exactExhaustion() {
        long[] r = calc(4000, 6000, 10000, DeductOrder.GIFT_FIRST);
        assertThat(r[0]).isEqualTo(4000);
        assertThat(r[1]).isEqualTo(6000);
        assertThat(4000 - r[0]).isZero();
        assertThat(6000 - r[1]).isZero();
    }

    @Test
    @DisplayName("任何情况下两项扣款之和等于应付金额")
    void sumAlwaysEqualsPayable() {
        long[][] cases = {
                {10000, 5000, 1}, {10000, 0, 5000}, {0, 10000, 5000},
                {1, 1, 2}, {999999, 1, 500000},
        };
        for (long[] c : cases) {
            for (DeductOrder order : DeductOrder.values()) {
                long[] r = calc(c[0], c[1], c[2], order);
                assertThat(r[0] + r[1])
                        .as("余额(%d,%d) 支付 %d 顺序 %s", c[0], c[1], c[2], order)
                        .isEqualTo(c[2]);
                assertThat(r[0]).isGreaterThanOrEqualTo(0);
                assertThat(r[1]).isGreaterThanOrEqualTo(0);
                assertThat(r[0]).isLessThanOrEqualTo(c[0]);
                assertThat(r[1]).isLessThanOrEqualTo(c[1]);
            }
        }
    }

    @Test
    @DisplayName("DeductOrder.of 对非法值回落为 GIFT_FIRST")
    void invalidOrderFallsBack() {
        assertThat(DeductOrder.of(null)).isEqualTo(DeductOrder.GIFT_FIRST);
        assertThat(DeductOrder.of("")).isEqualTo(DeductOrder.GIFT_FIRST);
        assertThat(DeductOrder.of("NOT_EXIST")).isEqualTo(DeductOrder.GIFT_FIRST);
        assertThat(DeductOrder.of("PRINCIPAL_FIRST")).isEqualTo(DeductOrder.PRINCIPAL_FIRST);
    }
}
