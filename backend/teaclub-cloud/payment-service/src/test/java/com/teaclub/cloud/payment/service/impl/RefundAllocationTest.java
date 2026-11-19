package com.teaclub.cloud.payment.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 退款资金构成分摊单元测试。
 *
 * <p>核心约束：实付部分 + 赠送部分 == 退款额，且任何一项都不超过原始扣款。
 */
class RefundAllocationTest {

    @Test
    @DisplayName("全额退款：实付与赠送按原始比例全额退回")
    void fullRefund() {
        long[] r = PaymentServiceImpl.allocateRefund(1000, 500, 1500, 1500);
        assertArrayEquals(new long[]{1000, 500}, r);
    }

    @Test
    @DisplayName("部分退款：实付按比例向下取整，赠送用减法兜底")
    void partialRefund() {
        // 原实付 1000、赠送 500、实付总额 1500，退 750
        // 实付部分 = floor(1000 * 750 / 1500) = 500，赠送 = 750 - 500 = 250
        long[] r = PaymentServiceImpl.allocateRefund(1000, 500, 1500, 750);
        assertEquals(500, r[0]);
        assertEquals(250, r[1]);
        assertEquals(750, r[0] + r[1], "两项之和必须精确等于退款额");
    }

    @Test
    @DisplayName("除不尽时合计仍精确等于退款额")
    void roundingStillExact() {
        long origPrincipal = 333;
        long origGift = 667;
        long paid = 1000;
        for (long amount = 1; amount <= paid; amount++) {
            long[] r = PaymentServiceImpl.allocateRefund(origPrincipal, origGift, paid, amount);
            assertEquals(amount, r[0] + r[1], "退款额 " + amount + " 分摊后合计不符");
            assertTrue(r[0] >= 0 && r[1] >= 0, "分摊不得为负");
            assertTrue(r[0] <= origPrincipal, "实付分摊不得超过原始实付");
            assertTrue(r[1] <= origGift, "赠送分摊不得超过原始赠送");
        }
    }

    @Test
    @DisplayName("纯实付订单：赠送分摊为 0")
    void principalOnly() {
        long[] r = PaymentServiceImpl.allocateRefund(2000, 0, 2000, 800);
        assertEquals(800, r[0]);
        assertEquals(0, r[1]);
    }

    @Test
    @DisplayName("实付为 0 时不做分摊，避免除零")
    void zeroPaid() {
        long[] r = PaymentServiceImpl.allocateRefund(0, 0, 0, 100);
        assertArrayEquals(new long[]{0, 0}, r);
    }
}
