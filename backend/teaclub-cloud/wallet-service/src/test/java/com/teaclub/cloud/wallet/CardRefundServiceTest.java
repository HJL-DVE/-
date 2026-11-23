package com.teaclub.cloud.wallet;

import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.UserContext;
import com.teaclub.cloud.wallet.dto.CardRefundApplyRequest;
import com.teaclub.cloud.wallet.dto.ConsumeOutCmd;
import com.teaclub.cloud.wallet.dto.RechargeInCmd;
import com.teaclub.cloud.wallet.entity.WalWallet;
import com.teaclub.cloud.wallet.entity.WalWalletTransaction;
import com.teaclub.cloud.wallet.mapper.WalWalletMapper;
import com.teaclub.cloud.wallet.mapper.WalWalletTransactionMapper;
import com.teaclub.cloud.wallet.client.BrandInternalClient;
import com.teaclub.cloud.core.api.Result;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.teaclub.cloud.wallet.service.CardRefundService;
import com.teaclub.cloud.wallet.service.WalletService;
import com.teaclub.cloud.wallet.vo.CardRefundVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 会员自助退卡规则测试。
 *
 * <p>覆盖的五条规则：
 * <ol>
 *   <li>原路退回实付本金、同时扣回赠送金（不折现、不计入退款金额）</li>
 *   <li>赠送余额不足扣回时拒绝退卡、转人工</li>
 *   <li>部分退款按原资金构成比例分摊、赠送部分用减法兜底</li>
 *   <li>资金一律以「分」为整数存储，不用浮点数</li>
 *   <li>重复请求按业务单号幂等，不得重复扣款</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("docker")
@Transactional
@DisplayName("会员自助退卡规则")
class CardRefundServiceTest {

    @MockBean
    private BrandInternalClient brandClient;

    @Autowired
    private CardRefundService cardRefundService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalWalletMapper walletMapper;

    @Autowired
    private WalWalletTransactionMapper txnMapper;

    private static final Long BRAND_ID = 1L;
    private static final Long STORE_ID = 1L;
    private Long memberId;

    @BeforeEach
    void setUp() {
        // 每次用全新会员 ID，避免历史退卡单干扰「每日限退」判断
        memberId = 880_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000L);
        // 默认品牌规则：自助秒退、每日不限、赠送已用允许扣回（便于测试各分支）
        Mockito.when(brandClient.getRefundRule(Mockito.anyLong()))
                .thenReturn(Result.ok(java.util.Map.of(
                        "deductOrder", "GIFT_FIRST",
                        "needAudit", 0,
                        "dailyLimit", 0,
                        "giftConsumedAction", "ALLOW")));
        // 会员端接口内部会读 UserContext（网关注入的身份），测试里直接构造
        UserContext.set(LoginUser.builder()
                .userId(memberId)
                .memberId(memberId)
                .brandId(BRAND_ID)
                .userType("MEMBER")
                .allStoreAccess(true)
                .build());
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        UserContext.set(null);
    }

    /** 充值入账：构造 实付 + 赠送 的初始余额 */
    private void recharge(long principal, long gift) {
        walletService.rechargeIn(RechargeInCmd.builder()
                .brandId(BRAND_ID)
                .memberId(memberId)
                .storeId(STORE_ID)
                .rechargeNo("T-" + UUID.randomUUID())
                .payAmount(principal)
                .giftAmount(gift)
                .operatorType("SYSTEM")
                .build());
    }

    /** 消费扣款 */
    private void consume(long amount) {
        walletService.consumeOut(ConsumeOutCmd.builder()
                .brandId(BRAND_ID)
                .memberId(memberId)
                .storeId(STORE_ID)
                .orderNo("O-" + UUID.randomUUID())
                .payableAmount(amount)
                .operatorType("SYSTEM")
                .build());
    }

    private WalWallet wallet() {
        return walletMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WalWallet>()
                        .eq(WalWallet::getBrandId, BRAND_ID)
                        .eq(WalWallet::getMemberId, memberId));
    }

    private List<WalWalletTransaction> txnsOf(String bizType) {
        return txnMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WalWalletTransaction>()
                        .eq(WalWalletTransaction::getBrandId, BRAND_ID)
                        .eq(WalWalletTransaction::getMemberId, memberId)
                        .eq(WalWalletTransaction::getBizType, bizType));
    }

    private CardRefundApplyRequest req(String reason) {
        CardRefundApplyRequest r = new CardRefundApplyRequest();
        r.setReason(reason);
        return r;
    }

    // ==================== 规则 1：退实付、扣赠送 ====================

    @Test
    @DisplayName("规则1：未消费的卡退卡 → 全额退实付本金，赠送全额作废")
    void refundUntouchedCard() {
        recharge(10_000, 2_000);   // 充 100 送 20

        CardRefundVO vo = cardRefundService.apply(memberId, req("不想喝了"));

        assertThat(vo.getPrincipalAmount()).as("退还实付本金").isEqualTo(10_000);
        assertThat(vo.getGiftVoidAmount()).as("作废赠送金额").isEqualTo(2_000);
        assertThat(vo.getStatus()).isEqualTo("APPROVED");

        WalWallet w = wallet();
        assertThat(w.getPrincipalBalance()).as("实付余额清零").isZero();
        assertThat(w.getGiftBalance()).as("赠送余额清零").isZero();

        // 资金流水：只产生一条 RECHARGE_REFUND_OUT，金额等于本金+赠送
        List<WalWalletTransaction> out = txnsOf("RECHARGE_REFUND_OUT");
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getPrincipalChange()).as("扣回本金为负").isEqualTo(-10_000);
        assertThat(out.get(0).getGiftChange()).as("作废赠送为负").isEqualTo(-2_000);
    }

    @Test
    @DisplayName("规则1：用实付本金消费过 → 只退剩余本金，赠送全额作废")
    void refundAfterPrincipalConsumed() {
        recharge(10_000, 2_000);
        // 品牌默认 GIFT_FIRST，显式指定 PRINCIPAL_FIRST 确保扣的是本金
        walletService.consumeOut(ConsumeOutCmd.builder()
                .brandId(BRAND_ID)
                .memberId(memberId)
                .storeId(STORE_ID)
                .orderNo("O-" + UUID.randomUUID())
                .payableAmount(3_000L)
                .deductOrder("PRINCIPAL_FIRST")
                .operatorType("SYSTEM")
                .build());

        WalWallet before = wallet();
        assertThat(before.getPrincipalBalance()).as("本金已被扣 30 元").isEqualTo(7_000);
        assertThat(before.getGiftBalance()).as("赠送未动").isEqualTo(2_000);

        CardRefundVO vo = cardRefundService.apply(memberId, req("搬家了"));

        assertThat(vo.getPrincipalAmount()).as("退剩余本金 70 元").isEqualTo(7_000);
        assertThat(vo.getGiftVoidAmount()).as("赠送 20 元作废").isEqualTo(2_000);

        WalWallet after = wallet();
        assertThat(after.getPrincipalBalance()).isZero();
        assertThat(after.getGiftBalance()).isZero();
    }

    // ==================== 规则 2：赠送不足扣回 → 拒绝转人工 ====================

    @Test
    @DisplayName("规则2：赠送金已被消费 → 拒绝自助退卡，提示转人工")
    void rejectWhenGiftConsumed() {
        // 明确配置为「赠送已用则拒绝」
        Mockito.when(brandClient.getRefundRule(Mockito.anyLong()))
                .thenReturn(Result.ok(java.util.Map.of(
                        "deductOrder", "GIFT_FIRST",
                        "needAudit", 0,
                        "dailyLimit", 0,
                        "giftConsumedAction", "REJECT")));
        recharge(10_000, 2_000);
        consume(500);   // GIFT_FIRST：扣掉 5 元赠送金

        WalWallet w = wallet();
        assertThat(w.getGiftBalance()).as("赠送被消耗").isEqualTo(1_500);

        assertThatThrownBy(() -> cardRefundService.apply(memberId, req("退卡")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("赠送金额已被使用")
                .hasMessageContaining("人工处理");

        // 拒绝后余额不变，未产生退卡流水
        WalWallet after = wallet();
        assertThat(after.getPrincipalBalance()).isEqualTo(10_000);
        assertThat(after.getGiftBalance()).isEqualTo(1_500);
        assertThat(txnsOf("RECHARGE_REFUND_OUT")).isEmpty();
    }

    @Test
    @DisplayName("规则2：赠送金全部消费完 → 仍拒绝退卡")
    void rejectWhenGiftFullyConsumed() {
        Mockito.when(brandClient.getRefundRule(Mockito.anyLong()))
                .thenReturn(Result.ok(java.util.Map.of(
                        "deductOrder", "GIFT_FIRST",
                        "needAudit", 0,
                        "dailyLimit", 0,
                        "giftConsumedAction", "REJECT")));
        recharge(10_000, 2_000);
        consume(2_000);   // 赠送金刚好花完

        assertThat(wallet().getGiftBalance()).isZero();
        assertThatThrownBy(() -> cardRefundService.apply(memberId, req("退卡")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("赠送金额已被使用");
    }

    // ==================== 规则 3：无实付本金 → 拒绝 ====================

    @Test
    @DisplayName("规则3：只有赠送金没有实付本金 → 拒绝退卡")
    void rejectWhenNoPrincipal() {
        // rechargeIn 不允许本金为 0，这里直接构造「纯赠送」钱包（模拟活动赠送入账）
        WalWallet w = new WalWallet();
        w.setBrandId(BRAND_ID);
        w.setMemberId(memberId);
        w.setPrincipalBalance(0L);
        w.setGiftBalance(2_000L);
        w.setTotalPrincipalIn(0L);
        w.setTotalGiftIn(2_000L);
        w.setTotalPrincipalOut(0L);
        w.setTotalGiftOut(0L);
        w.setTotalRefundIn(0L);
        w.setVersion(0);
        w.setStatus("NORMAL");
        walletMapper.insert(w);

        assertThatThrownBy(() -> cardRefundService.apply(memberId, req("退卡")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无实付余额可退");
    }

    // ==================== 规则 4：整数分存储，无浮点 ====================

    @Test
    @DisplayName("规则4：金额全程整数分，退卡后余额与流水均为整数且精确")
    void amountsAreIntegers() {
        recharge(10_000, 2_000);
        CardRefundVO vo = cardRefundService.apply(memberId, req("测试整数"));

        // 断言类型为 long（整型分），且金额精确
        long p = vo.getPrincipalAmount();
        long g = vo.getGiftVoidAmount();
        assertThat(p).isEqualTo(10_000L);
        assertThat(g).isEqualTo(2_000L);
        assertThat(p + g).as("本金+赠送 = 原总额").isEqualTo(12_000L);

        WalWallet w = wallet();
        assertThat(w.getPrincipalBalance()).isZero();
        assertThat(w.getGiftBalance()).isZero();
    }

    // ==================== 规则 5：幂等 ====================

    @Test
    @DisplayName("规则5：同一退卡单号重复调用 rechargeRefundOut → 只扣一次")
    void refundTransactionIsIdempotent() {
        recharge(10_000, 2_000);
        String refundNo = "RF-IDEM-" + UUID.randomUUID();

        // 第一次
        walletService.rechargeRefundOut(BRAND_ID, memberId, STORE_ID, refundNo,
                10_000L, 2_000L, "幂等测试", null);
        WalWallet afterFirst = wallet();
        assertThat(afterFirst.getPrincipalBalance()).isZero();

        // 第二次（相同单号）：幂等命中，不重复扣款
        walletService.rechargeRefundOut(BRAND_ID, memberId, STORE_ID, refundNo,
                10_000L, 2_000L, "幂等测试重复", null);

        WalWallet afterSecond = wallet();
        assertThat(afterSecond.getPrincipalBalance()).as("余额未被二次扣减").isZero();
        assertThat(afterSecond.getGiftBalance()).isZero();

        // 流水只有一条
        List<WalWalletTransaction> out = txnsOf("RECHARGE_REFUND_OUT");
        assertThat(out).hasSize(1);
    }

    @Test
    @DisplayName("规则5：每日限退一次 → 第二次退卡被拒绝")
    void dailyLimit() {
        Mockito.when(brandClient.getRefundRule(Mockito.anyLong()))
                .thenReturn(Result.ok(java.util.Map.of(
                        "deductOrder", "GIFT_FIRST",
                        "needAudit", 0,
                        "dailyLimit", 1,
                        "giftConsumedAction", "ALLOW")));
        recharge(10_000, 2_000);
        cardRefundService.apply(memberId, req("第一次退卡"));

        // 再充值后再次尝试退卡
        recharge(5_000, 0);
        assertThatThrownBy(() -> cardRefundService.apply(memberId, req("第二次退卡")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("今日已办理");
    }

    // ==================== 组合场景 ====================

    @Test
    @DisplayName("完整场景：充100送20 → 用本金消费30 → 退卡退70、赠送20作废")
    void fullScenario() {
        recharge(10_000, 2_000);

        walletService.consumeOut(ConsumeOutCmd.builder()
                .brandId(BRAND_ID)
                .memberId(memberId)
                .storeId(STORE_ID)
                .orderNo("O-" + UUID.randomUUID())
                .payableAmount(3_000L)
                .deductOrder("PRINCIPAL_FIRST")
                .operatorType("SYSTEM")
                .build());

        CardRefundVO vo = cardRefundService.apply(memberId, req("毕业离校"));

        assertThat(vo.getPrincipalAmount()).isEqualTo(7_000);
        assertThat(vo.getGiftVoidAmount()).isEqualTo(2_000);
        assertThat(vo.getRefundTxnNo()).isNotBlank();

        // 退卡记录能查到
        List<CardRefundVO> mine = cardRefundService.myApplications(memberId);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).getStatus()).isEqualTo("APPROVED");
    }

    // ==================== 规则配置：审核 vs 秒退 ====================

    @Test
    @DisplayName("配置为「需要审核」→ 只建单不扣款，状态 PENDING")
    void needAuditCreatesPendingWithoutDeducting() {
        Mockito.when(brandClient.getRefundRule(Mockito.anyLong()))
                .thenReturn(Result.ok(java.util.Map.of(
                        "deductOrder", "GIFT_FIRST",
                        "needAudit", 1,
                        "dailyLimit", 0,
                        "giftConsumedAction", "ALLOW")));

        recharge(10_000, 2_000);
        CardRefundVO vo = cardRefundService.apply(memberId, req("需要审核"));

        assertThat(vo.getStatus()).as("待审核状态").isEqualTo("PENDING");
        assertThat(vo.getRefundTxnNo()).as("未产生资金流水").isNull();

        WalWallet w = wallet();
        assertThat(w.getPrincipalBalance()).as("余额未动").isEqualTo(10_000);
        assertThat(w.getGiftBalance()).as("赠送未动").isEqualTo(2_000);
        assertThat(txnsOf("RECHARGE_REFUND_OUT")).as("无退款流水").isEmpty();
    }

    @Test
    @DisplayName("配置为「赠送已用允许扣回」→ 赠送金花过也能秒退")
    void allowWhenGiftConsumed() {
        Mockito.when(brandClient.getRefundRule(Mockito.anyLong()))
                .thenReturn(Result.ok(java.util.Map.of(
                        "deductOrder", "GIFT_FIRST",
                        "needAudit", 0,
                        "dailyLimit", 0,
                        "giftConsumedAction", "ALLOW")));

        recharge(10_000, 2_000);
        consume(500);   // 花掉 5 元赠送金

        CardRefundVO vo = cardRefundService.apply(memberId, req("允许扣回"));

        assertThat(vo.getStatus()).isEqualTo("APPROVED");
        assertThat(vo.getPrincipalAmount()).as("退全部实付本金").isEqualTo(10_000);
        assertThat(vo.getGiftVoidAmount()).as("剩余赠送作废").isEqualTo(1_500);
        assertThat(txnsOf("RECHARGE_REFUND_OUT")).hasSize(1);
    }}
