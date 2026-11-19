package com.teaclub.cloud.wallet;

import com.teaclub.cloud.wallet.dto.ConsumeOutCmd;
import com.teaclub.cloud.wallet.dto.RechargeInCmd;
import com.teaclub.cloud.wallet.entity.WalWallet;
import com.teaclub.cloud.wallet.mapper.WalWalletTransactionMapper;
import com.teaclub.cloud.wallet.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钱包并发扣款测试。
 *
 * <p><b>关键场景</b>：余额 100 元，10 个线程并发各扣 15 元。
 * 正确行为：只有 6 个成功（100 / 15），总扣款 90 元，余额剩 10 元，
 * <b>绝不能扣成负数</b>。
 *
 * <p>本测试不加 @Transactional，因为要验证真实的独立事务并发行为。
 */
@SpringBootTest
@ActiveProfiles("docker")
class WalletConcurrencyTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalWalletTransactionMapper txnMapper;

    private static final Long BRAND_ID = 1L;
    private static final Long MEMBER_ID = 900002L;
    private static final Long STORE_ID = 1L;

    @Test
    @DisplayName("并发扣款：不超扣、不出现负余额")
    void concurrentConsumeNeverOverdraws() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        long memberId = MEMBER_ID;

        // 充值 100 元
        walletService.rechargeIn(RechargeInCmd.builder()
                .brandId(BRAND_ID).memberId(memberId).storeId(STORE_ID)
                .rechargeNo("RC-CONC-" + tag).payAmount(10000L).giftAmount(0L).build());

        int threads = 10;
        long each = 1500L;   // 10 * 1500 = 15000 > 10000，必须有部分失败
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<String> orderNos = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            String orderNo = "TC-CONC-" + tag + "-" + i;
            orderNos.add(orderNo);
            pool.submit(() -> {
                try {
                    start.await();
                    walletService.consumeOut(ConsumeOutCmd.builder()
                            .brandId(BRAND_ID).memberId(memberId).storeId(STORE_ID)
                            .orderNo(orderNo).payableAmount(each)
                            .deductOrder("GIFT_FIRST").build());
                    success.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        WalWallet wallet = walletService.getWallet(BRAND_ID, memberId);

        // 余额不能为负
        assertThat(wallet.getPrincipalBalance()).isNotNegative();
        assertThat(wallet.getGiftBalance()).isNotNegative();

        // 成功数 * 扣款额 <= 初始余额
        long totalDeduct = success.get() * each;
        assertThat(totalDeduct).isLessThanOrEqualTo(10000L);

        // 余额 = 初始 - 实际扣款
        assertThat(wallet.getPrincipalBalance()).isEqualTo(10000L - totalDeduct);

        // 成功 + 失败 = 总数
        assertThat(success.get() + failed.get()).isEqualTo(threads);

        // 流水条数 = 成功数（失败的不写流水）
        int txnCount = 0;
        for (String no : orderNos) {
            txnCount += txnMapper.countByBiz("CONSUME_OUT", no);
        }
        assertThat(txnCount).isEqualTo(success.get());

        System.out.printf(
                "[并发扣款] 初始=10000 分, 并发=%d 次 x %d 分 -> 成功=%d 失败=%d 余额=%d 分%n",
                threads, each, success.get(), failed.get(), wallet.getPrincipalBalance());
    }

    @Test
    @DisplayName("并发重复同一订单号：只扣一次")
    void concurrentSameOrderOnlyDeductsOnce() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        long memberId = 900003L;
        String orderNo = "TC-SAME-" + tag;

        walletService.rechargeIn(RechargeInCmd.builder()
                .brandId(BRAND_ID).memberId(memberId).storeId(STORE_ID)
                .rechargeNo("RC-SAME-" + tag).payAmount(10000L).giftAmount(0L).build());

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    walletService.consumeOut(ConsumeOutCmd.builder()
                            .brandId(BRAND_ID).memberId(memberId).storeId(STORE_ID)
                            .orderNo(orderNo).payableAmount(3000L)
                            .deductOrder("GIFT_FIRST").build());
                } catch (Exception ignored) {
                    // 部分线程可能因唯一键冲突抛异常，属预期
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        WalWallet wallet = walletService.getWallet(BRAND_ID, memberId);

        // 只扣了 3000，剩 7000
        assertThat(wallet.getPrincipalBalance()).isEqualTo(7000L);

        // 只有一条流水
        assertThat(txnMapper.countByBiz("CONSUME_OUT", orderNo)).isEqualTo(1);

        System.out.printf("[并发同单号] 8 线程同订单扣 3000 分 -> 实际扣款 %d 分, 流水 %d 条%n",
                10000L - wallet.getPrincipalBalance(),
                txnMapper.countByBiz("CONSUME_OUT", orderNo));
    }
}
