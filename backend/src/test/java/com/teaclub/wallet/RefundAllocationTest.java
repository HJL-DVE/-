package com.teaclub.wallet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 退款资金构成分摊测试。
 *
 * <p>核心要求：部分退款时退回的实付金 + 赠送金必须<b>精确等于</b>退款金额，
 * 不能因为四舍五入出现 1 分钱对不平。
 */
class RefundAllocationTest {

    /**
     * 与 RefundService 将采用的算法一致：
     * 先按比例算实付部分（向下取整），赠送部分用减法兜底。
     */
    static long[] allocate(long originalPrincipal, long originalGift,
                           long originalPaid, long refundAmount) {
        if (originalPaid <= 0) {
            return new long[]{0, 0};
        }
        if (refundAmount >= originalPaid) {
            return new long[]{originalPrincipal, originalGift};
        }
        long principalPart = BigDecimal.valueOf(originalPrincipal)
                .multiply(BigDecimal.valueOf(refundAmount))
                .divide(BigDecimal.valueOf(originalPaid), 0, RoundingMode.DOWN)
                .longValueExact();
        long giftPart = refundAmount - principalPart;
        return new long[]{principalPart, giftPart};
    }

    @Test
    @DisplayName("全额退款：原样退回实付与赠送构成")
    void fullRefund() {
        long[] r = allocate(7000, 3000, 10000, 10000);
        assertThat(r[0]).isEqualTo(7000);
        assertThat(r[1]).isEqualTo(3000);
    }

    @Test
    @DisplayName("部分退款：两项之和精确等于退款额")
    void partialRefundSumsExactly() {
        long[] r = allocate(7000, 3000, 10000, 3333);
        assertThat(r[0] + r[1]).isEqualTo(3333);
    }

    @Test
    @DisplayName("除不尽时用减法兜底，不出现 1 分钱误差")
    void noRoundingDrift() {
        // 10000 分中实付 3333、赠送 6667，退 1 分
        for (long refund = 1; refund <= 10000; refund += 137) {
            long[] r = allocate(3333, 6667, 10000, refund);
            assertThat(r[0] + r[1])
                    .as("退款 %d 分时构成不一致", refund)
                    .isEqualTo(refund);
            assertThat(r[0]).isGreaterThanOrEqualTo(0);
            assertThat(r[1]).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @DisplayName("全部退还后再退一次仍不应超额（由上层状态机拦截）")
    void allocationNeverExceedsOriginal() {
        long[] r = allocate(5000, 5000, 10000, 10000);
        assertThat(r[0]).isLessThanOrEqualTo(5000);
        assertThat(r[1]).isLessThanOrEqualTo(5000);
    }

    @Test
    @DisplayName("纯实付支付退款：赠送部分为 0")
    void purePrincipal() {
        long[] r = allocate(5000, 0, 5000, 2000);
        assertThat(r[0]).isEqualTo(2000);
        assertThat(r[1]).isZero();
    }

    @Test
    @DisplayName("纯赠送支付退款：实付部分为 0")
    void pureGift() {
        long[] r = allocate(0, 5000, 5000, 2000);
        assertThat(r[0]).isZero();
        assertThat(r[1]).isEqualTo(2000);
    }
}
