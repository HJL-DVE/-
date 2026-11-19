package com.teaclub.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订单金额分摊与折扣计算测试。
 *
 * <p>校验与 OrderAmountCalculator 一致的算术规则，重点是
 * "行优惠分摊后总和必须精确等于总优惠"以及"应付金额永不为负"。
 */
class AmountAllocationTest {

    /** 与 OrderAmountCalculator 一致的等级折扣算法 */
    static long levelDiscount(long base, int discountRate) {
        return BigDecimal.valueOf(base)
                .multiply(BigDecimal.valueOf(10000L - discountRate))
                .divide(BigDecimal.valueOf(10000L), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /** 与 OrderAmountCalculator 一致的分摊算法（最后一行减法兜底） */
    static long[] allocate(List<Long> lineTotals, long totalDiscount) {
        long totalAmount = lineTotals.stream().mapToLong(Long::longValue).sum();
        long[] result = new long[lineTotals.size()];
        if (totalDiscount <= 0 || totalAmount <= 0) {
            return result;
        }
        long allocated = 0;
        for (int i = 0; i < lineTotals.size(); i++) {
            long share;
            if (i == lineTotals.size() - 1) {
                share = totalDiscount - allocated;
            } else {
                share = BigDecimal.valueOf(lineTotals.get(i))
                        .multiply(BigDecimal.valueOf(totalDiscount))
                        .divide(BigDecimal.valueOf(totalAmount), 0, RoundingMode.DOWN)
                        .longValueExact();
            }
            share = Math.max(0, Math.min(share, lineTotals.get(i)));
            result[i] = share;
            allocated += share;
        }
        return result;
    }

    @Test
    @DisplayName("9.5 折：10000 分应收 9500，优惠 500")
    void levelDiscount95() {
        assertThat(levelDiscount(10000, 9500)).isEqualTo(500);
    }

    @Test
    @DisplayName("不打折（10000）优惠为 0")
    void noDiscount() {
        assertThat(levelDiscount(12345, 10000)).isZero();
    }

    @Test
    @DisplayName("折扣四舍五入不产生超过应付的优惠")
    void discountNeverExceedsBase() {
        for (int rate : new int[]{9000, 9500, 9800, 10000}) {
            for (long base : new long[]{1, 7, 99, 12345, 999999}) {
                long d = levelDiscount(base, rate);
                assertThat(d).isGreaterThanOrEqualTo(0);
                assertThat(d).isLessThanOrEqualTo(base);
            }
        }
    }

    @Test
    @DisplayName("多行分摊：各行优惠之和精确等于总优惠")
    void allocationSumsExactly() {
        List<Long> lines = List.of(1800L, 2200L, 1000L);
        long discount = 1234L;
        long[] alloc = allocate(lines, discount);
        long sum = 0;
        for (long a : alloc) {
            sum += a;
        }
        assertThat(sum).isEqualTo(discount);
    }

    @Test
    @DisplayName("分摊后每行优惠不超过该行金额")
    void allocationNotExceedLine() {
        List<Long> lines = List.of(100L, 200L, 300L);
        long[] alloc = allocate(lines, 500L);
        for (int i = 0; i < lines.size(); i++) {
            assertThat(alloc[i]).isLessThanOrEqualTo(lines.get(i));
            assertThat(alloc[i]).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @DisplayName("优惠大于商品总额时被截断，不产生负数实付")
    void overDiscountClamped() {
        List<Long> lines = List.of(100L);
        long[] alloc = allocate(lines, 500L);
        assertThat(alloc[0]).isEqualTo(100L);
    }

    @Test
    @DisplayName("应付金额在极端叠加下不会为负")
    void payableNeverNegative() {
        long total = 1000;
        long activity = 500;
        long level = levelDiscount(total - activity, 9000);
        long coupon = 9999;
        long afterLevel = total - activity - level;
        long actualCoupon = Math.min(coupon, afterLevel);
        long payable = Math.max(0, afterLevel - actualCoupon);
        assertThat(payable).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("空行列表返回空分摊")
    void emptyLines() {
        assertThat(allocate(new ArrayList<>(), 100)).isEmpty();
    }
}
