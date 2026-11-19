package com.teaclub.cloud.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 业务单号生成器测试。
 *
 * <p>单号是资金幂等键，必须满足：前缀正确、长度稳定、批量生成不重复。
 */
class BizNoGeneratorTest {

    @Test
    @DisplayName("各单号前缀正确")
    void prefixes() {
        assertThat(BizNoGenerator.orderNo()).startsWith("TC");
        assertThat(BizNoGenerator.rechargeNo()).startsWith("RC");
        assertThat(BizNoGenerator.paymentNo()).startsWith("PAY");
        assertThat(BizNoGenerator.refundNo()).startsWith("RF");
        assertThat(BizNoGenerator.walletTxnNo()).startsWith("WT");
        assertThat(BizNoGenerator.approvalNo()).startsWith("AP");
    }

    @Test
    @DisplayName("批量生成不重复")
    void uniqueInBatch() {
        Set<String> set = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            set.add(BizNoGenerator.paymentNo());
        }
        assertThat(set).hasSize(2000);
    }

    @Test
    @DisplayName("单号长度稳定")
    void lengthStable() {
        assertThat(BizNoGenerator.orderNo().length()).isEqualTo(2 + 14 + 6);
    }
}
