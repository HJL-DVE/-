package com.teaclub.wallet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 资金不变量测试（纯逻辑，不依赖数据库）。
 *
 * <p>验证 I1~I5 在模拟的流水序列上恒成立：
 * <pre>
 * I1 余额永不为负
 * I2/I3 余额 = 所有流水的变动额之和
 * I4/I5 after = before + change
 * </pre>
 */
class WalletInvariantTest {

    /** 一条流水的快照 */
    record Txn(long principalChange, long giftChange,
               long principalBefore, long principalAfter,
               long giftBefore, long giftAfter) {
    }

    /** 按顺序累加流水，模拟钱包的记账过程 */
    static List<Txn> applySequence(long[][] changes) {
        long p = 0, g = 0;
        List<Txn> out = new ArrayList<>();
        for (long[] c : changes) {
            long pAfter = p + c[0];
            long gAfter = g + c[1];
            if (pAfter < 0 || gAfter < 0) {
                throw new IllegalStateException("余额不能为负: principal=" + pAfter + " gift=" + gAfter);
            }
            out.add(new Txn(c[0], c[1], p, pAfter, g, gAfter));
            p = pAfter;
            g = gAfter;
        }
        return out;
    }

    @Test
    @DisplayName("I4/I5: 每条流水 after = before + change")
    void afterEqualsBeforePlusChange() {
        var txns = applySequence(new long[][]{
                {20000, 3000},   // 充值
                {-500, -3000},   // 消费（赠送优先）
                {500, 3000},     // 退款回冲
        });
        for (Txn t : txns) {
            assertThat(t.principalAfter()).isEqualTo(t.principalBefore() + t.principalChange());
            assertThat(t.giftAfter()).isEqualTo(t.giftBefore() + t.giftChange());
        }
    }

    @Test
    @DisplayName("I2/I3: 余额等于所有流水变动额之和")
    void balanceEqualsSumOfChanges() {
        var txns = applySequence(new long[][]{
                {10000, 1000},
                {-2000, -1000},
                {500, 0},
                {-3000, 0},
        });
        long sumP = txns.stream().mapToLong(Txn::principalChange).sum();
        long sumG = txns.stream().mapToLong(Txn::giftChange).sum();
        Txn last = txns.get(txns.size() - 1);
        assertThat(last.principalAfter()).isEqualTo(sumP);
        assertThat(last.giftAfter()).isEqualTo(sumG);
    }

    @Test
    @DisplayName("I6: 相邻流水首尾相接（before 等于上一条 after）")
    void ledgerIsChained() {
        var txns = applySequence(new long[][]{
                {10000, 0}, {-1000, 0}, {0, 500}, {-3000, -500},
        });
        for (int i = 1; i < txns.size(); i++) {
            assertThat(txns.get(i).principalBefore()).isEqualTo(txns.get(i - 1).principalAfter());
            assertThat(txns.get(i).giftBefore()).isEqualTo(txns.get(i - 1).giftAfter());
        }
    }

    @Test
    @DisplayName("I1: 任何会导致负余额的序列都被拒绝")
    void negativeBalanceRejected() {
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> applySequence(new long[][]{{1000, 0}, {-1001, 0}})).getMessage())
                .contains("余额不能为负");
    }

    @Test
    @DisplayName("恰好扣完到 0 是合法的")
    void exactZeroIsValid() {
        var txns = applySequence(new long[][]{{1000, 500}, {-1000, -500}});
        Txn last = txns.get(txns.size() - 1);
        assertThat(last.principalAfter()).isZero();
        assertThat(last.giftAfter()).isZero();
    }

    @Test
    @DisplayName("实付与赠送分别记账，互不串账")
    void principalAndGiftNeverMixed() {
        var txns = applySequence(new long[][]{{0, 5000}, {3000, 0}});
        assertThat(txns.get(0).principalAfter()).as("只加赠送时实付不变").isZero();
        assertThat(txns.get(0).giftAfter()).isEqualTo(5000);
        assertThat(txns.get(1).principalAfter()).isEqualTo(3000);
        assertThat(txns.get(1).giftAfter()).as("加实付时赠送不变").isEqualTo(5000);
    }
}
