package com.teaclub.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 退款资金构成分摊测试（与 PaymentServiceImpl.allocateRefund 逻辑一致）。
 *
 * <p>硬性要求：{@code 实付部分 + 赠送部分 == 退款金额}，不允许出现 1 分钱误差。
 */
class RefundAllocationConsistencyTest {

    static long[] allocate(long origPrincipal, long origGift, long origPaid, long refundAmount) {
        if (origPaid <= 0) {
            return new long[]{0, 0};
        }
        if (refundAmount >= origPaid) {
            return new long[]{origPrincipal, origGift};
        }
        long principalPart = BigDecimal.valueOf(origPrincipal)
                .multiply(BigDecimal.valueOf(refundAmount))
                .divide(BigDecimal.valueOf(origPaid), 0, RoundingMode.DOWN)
                .longValueExact();
        if (principalPart > origPrincipal) {
            principalPart = origPrincipal;
        }
        long giftPart = refundAmount - principalPart;
        if (giftPart < 0) {
            giftPart = 0;
            principalPart = refundAmount;
        }
        return new long[]{principalPart, giftPart};
    }

    @Test
    @DisplayName("全额退款原样返回原构成")
    void fullRefund() {
        long[] r = allocate(500, 3000, 3500, 3500);
        assertThat(r[0]).isEqualTo(500);
        assertThat(r[1]).isEqualTo(3000);
    }

    @Test
    @DisplayName("任意部分退款：两项之和精确等于退款额")
    void partialAlwaysExact() {
        long[][] cases = {
                {500, 3000, 3500, 1},
                {500, 3000, 3500, 777},
                {500, 3000, 3500, 3499},
                {3333, 6667, 10000, 1},
                {1, 9999, 10000, 5000},
        };
        for (long[] c : cases) {
            long[] r = allocate(c[0], c[1], c[2], c[3]);
            assertThat(r[0] + r[1])
                    .as("构成(%d,%d) 实付%d 退%d", c[0], c[1], c[2], c[3])
                    .isEqualTo(c[3]);
            assertThat(r[0]).isGreaterThanOrEqualTo(0);
            assertThat(r[1]).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @DisplayName("退款构成不超过原始扣款构成")
    void neverExceedOriginal() {
        long[] r = allocate(500, 3000, 3500, 3500);
        assertThat(r[0]).isLessThanOrEqualTo(500);
        assertThat(r[1]).isLessThanOrEqualTo(3000);
    }

    @Test
    @DisplayName("纯实付支付：赠送部分为 0")
    void purePrincipal() {
        long[] r = allocate(1000, 0, 1000, 400);
        assertThat(r[0]).isEqualTo(400);
        assertThat(r[1]).isZero();
    }

    @Test
    @DisplayName("纯赠送支付：实付部分为 0")
    void pureGift() {
        long[] r = allocate(0, 1000, 1000, 400);
        assertThat(r[0]).isZero();
        assertThat(r[1]).isEqualTo(400);
    }
}
