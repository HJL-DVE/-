package com.teaclub.cloud.wallet.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.api.TraceIdHolder;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.core.util.BizNoGenerator;
import com.teaclub.cloud.wallet.dto.ConsumeOutCmd;
import com.teaclub.cloud.wallet.dto.RechargeInCmd;
import com.teaclub.cloud.wallet.dto.RefundInCmd;
import com.teaclub.cloud.wallet.dto.WalletChangeResult;
import com.teaclub.cloud.wallet.entity.WalWallet;
import com.teaclub.cloud.wallet.entity.WalRechargeOrder;
import com.teaclub.cloud.wallet.entity.WalWalletTransaction;
import com.teaclub.cloud.wallet.enums.DeductOrder;
import com.teaclub.cloud.wallet.enums.WalletBizType;
import com.teaclub.cloud.wallet.mapper.WalWalletMapper;
import com.teaclub.cloud.wallet.mapper.WalWalletTransactionMapper;
import com.teaclub.cloud.wallet.service.WalletService;
import com.teaclub.cloud.wallet.service.support.DeductOrderResolver;
import com.teaclub.cloud.redis.cache.HotDataCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 钱包服务实现（由单体 backend 的 WalletServiceImpl 迁移而来）。
 *
 * <p>并发控制方案：<b>悲观行锁（SELECT ... FOR UPDATE）为主</b>。
 * 在持有钱包行锁的前提下读取余额 → 计算变动 → 写流水 → 更新余额，
 * 整个序列在同一个数据库事务内完成，因此不存在"读到过期余额"的可能。
 *
 * <p>幂等由三层保证：
 * <ol>
 *   <li>进入时先查 biz_no 对应流水，已存在直接返回</li>
 *   <li>插入流水时 uk_txn_biz 唯一索引兜底</li>
 *   <li>业务单自身的状态机（如充值单 PAID）再兜一层</li>
 * </ol>
 *
 * <p><b>跨服务调整</b>：品牌扣款顺序原从 `sys_brand` 表直接读取，
 * 迁移后改为通过 Feign 调用 auth-service 获取（品牌配置属 auth-service 域），
 * 并在 Redis 中短暂缓存。
 *
 * <p><b>Redis 分布式锁不是资金一致性的保障</b>，仅用于降低并发冲突；
 * 真正的一致性由数据库行锁 + 唯一索引保证。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalWalletMapper walletMapper;
    private final WalWalletTransactionMapper txnMapper;
    private final DeductOrderResolver deductOrderResolver;
    private final HotDataCache hotDataCache;
    private final com.teaclub.cloud.wallet.mapper.WalRechargeOrderMapper rechargeOrderMapper;

    // ==================== 查询 ====================

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public WalWallet getOrCreateWallet(Long brandId, Long memberId) {
        WalWallet wallet = walletMapper.selectOne(Wrappers.<WalWallet>lambdaQuery()
                .eq(WalWallet::getBrandId, brandId)
                .eq(WalWallet::getMemberId, memberId));
        if (wallet != null) {
            return wallet;
        }
        WalWallet created = new WalWallet();
        created.setBrandId(brandId);
        created.setMemberId(memberId);
        created.setPrincipalBalance(0L);
        created.setGiftBalance(0L);
        created.setTotalPrincipalIn(0L);
        created.setTotalGiftIn(0L);
        created.setTotalPrincipalOut(0L);
        created.setTotalGiftOut(0L);
        created.setTotalRefundIn(0L);
        created.setVersion(0);
        created.setStatus("NORMAL");
        try {
            walletMapper.insert(created);
            return created;
        } catch (DuplicateKeyException e) {
            // 并发创建：对方先创建成功，重新读取
            return walletMapper.selectOne(Wrappers.<WalWallet>lambdaQuery()
                    .eq(WalWallet::getBrandId, brandId)
                    .eq(WalWallet::getMemberId, memberId));
        }
    }

    @Override
    public WalWallet getWallet(Long brandId, Long memberId) {
        WalWallet wallet = walletMapper.selectOne(Wrappers.<WalWallet>lambdaQuery()
                .eq(WalWallet::getBrandId, brandId)
                .eq(WalWallet::getMemberId, memberId));
        if (wallet == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "该会员尚未开通储值钱包");
        }
        return wallet;
    }

    // ==================== 充值入账 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletChangeResult rechargeIn(RechargeInCmd cmd) {
        validateCommon(cmd.getBrandId(), cmd.getMemberId(), cmd.getRechargeNo(), "充值单号");
        if (cmd.getPayAmount() == null || cmd.getPayAmount() <= 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "充值金额必须大于 0");
        }
        long giftAmount = cmd.getGiftAmount() == null ? 0L : cmd.getGiftAmount();
        if (giftAmount < 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "赠送金额不能为负");
        }

        // 幂等前置检查
        WalWalletTransaction existed = findTxn(WalletBizType.RECHARGE_IN, cmd.getRechargeNo());
        if (existed != null) {
            log.info("充值入账幂等命中 rechargeNo={}", cmd.getRechargeNo());
            return toResult(existed, true);
        }

        WalWallet wallet = lockWallet(cmd.getBrandId(), cmd.getMemberId());
        if (!"NORMAL".equals(wallet.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "钱包已被冻结，无法充值");
        }

        Long principalBefore = wallet.getPrincipalBalance();
        Long principalAfter = principalBefore + cmd.getPayAmount();
        Long giftBefore = wallet.getGiftBalance();
        Long giftAfter = giftBefore + giftAmount;
        assertNonNegative(principalAfter, giftAfter);

        WalWalletTransaction txn = buildTxn(wallet, cmd.getStoreId(),
                WalletBizType.RECHARGE_IN, cmd.getRechargeNo(),
                cmd.getPayAmount(), principalBefore, principalAfter,
                giftAmount, giftBefore, giftAfter);
        txn.setOperatorId(cmd.getOperatorId());
        txn.setOperatorType(defaultOperator(cmd.getOperatorType()));
        txn.setRemark("充值入账");
        txn.setAmountPaid(cmd.getPayAmount());
        txn.setGiftAmount(giftAmount);
        if (cmd.getGiftExpireDays() != null && cmd.getGiftExpireDays() > 0 && giftAmount > 0) {
            txn.setGiftExpireTime(LocalDateTime.now().plusDays(cmd.getGiftExpireDays()));
        }

        insertTxnIdempotent(txn);

        wallet.setPrincipalBalance(principalAfter);
        wallet.setGiftBalance(giftAfter);
        wallet.setTotalPrincipalIn(wallet.getTotalPrincipalIn() + cmd.getPayAmount());
        wallet.setTotalGiftIn(wallet.getTotalGiftIn() + giftAmount);
        updateWallet(wallet);
        evictCache(cmd.getBrandId(), cmd.getMemberId());

        log.info("充值入账成功 walletId={} rechargeNo={} 实付+{} 赠送+{} 余额({},{})",
                wallet.getId(), cmd.getRechargeNo(), cmd.getPayAmount(), giftAmount,
                principalAfter, giftAfter);
        return toResult(txn, false);
    }

    // ==================== 消费扣款 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletChangeResult consumeOut(ConsumeOutCmd cmd) {
        validateCommon(cmd.getBrandId(), cmd.getMemberId(), cmd.getOrderNo(), "订单号");
        if (cmd.getPayableAmount() == null || cmd.getPayableAmount() <= 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "应付金额必须大于 0");
        }

        WalWalletTransaction existed = findTxn(WalletBizType.CONSUME_OUT, cmd.getOrderNo());
        if (existed != null) {
            log.info("消费扣款幂等命中 orderNo={}", cmd.getOrderNo());
            return toResult(existed, true);
        }

        WalWallet wallet = lockWallet(cmd.getBrandId(), cmd.getMemberId());
        if (!"NORMAL".equals(wallet.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "钱包已被冻结，无法消费");
        }

        long payable = cmd.getPayableAmount();
        long total = wallet.getPrincipalBalance() + wallet.getGiftBalance();
        if (total < payable) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "余额不足：可用 " + total + " 分，需支付 " + payable + " 分");
        }

        DeductOrder order = deductOrderResolver.resolve(cmd.getBrandId(), cmd.getDeductOrder());

        long giftDeduct;
        long principalDeduct;
        if (order == DeductOrder.GIFT_FIRST) {
            giftDeduct = Math.min(wallet.getGiftBalance(), payable);
            principalDeduct = payable - giftDeduct;
        } else {
            principalDeduct = Math.min(wallet.getPrincipalBalance(), payable);
            giftDeduct = payable - principalDeduct;
        }

        Long principalBefore = wallet.getPrincipalBalance();
        Long principalAfter = principalBefore - principalDeduct;
        Long giftBefore = wallet.getGiftBalance();
        Long giftAfter = giftBefore - giftDeduct;
        assertNonNegative(principalAfter, giftAfter);

        WalWalletTransaction txn = buildTxn(wallet, cmd.getStoreId(),
                WalletBizType.CONSUME_OUT, cmd.getOrderNo(),
                -principalDeduct, principalBefore, principalAfter,
                -giftDeduct, giftBefore, giftAfter);
        txn.setOperatorId(cmd.getOperatorId());
        txn.setOperatorType(defaultOperator(cmd.getOperatorType()));
        txn.setRemark("消费扣款，扣款顺序=" + order.name());

        insertTxnIdempotent(txn);

        wallet.setPrincipalBalance(principalAfter);
        wallet.setGiftBalance(giftAfter);
        wallet.setTotalPrincipalOut(wallet.getTotalPrincipalOut() + principalDeduct);
        wallet.setTotalGiftOut(wallet.getTotalGiftOut() + giftDeduct);
        updateWallet(wallet);
        evictCache(cmd.getBrandId(), cmd.getMemberId());

        log.info("消费扣款成功 walletId={} orderNo={} 应付={} 实付扣={} 赠送扣={} 余额({},{})",
                wallet.getId(), cmd.getOrderNo(), payable, principalDeduct, giftDeduct,
                principalAfter, giftAfter);
        return toResult(txn, false);
    }

    // ==================== 退款回冲 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletChangeResult refundIn(RefundInCmd cmd) {
        validateCommon(cmd.getBrandId(), cmd.getMemberId(), cmd.getRefundNo(), "退款单号");
        long pAmt = cmd.getRefundPrincipalAmount() == null ? 0L : cmd.getRefundPrincipalAmount();
        long gAmt = cmd.getRefundGiftAmount() == null ? 0L : cmd.getRefundGiftAmount();
        if (pAmt < 0 || gAmt < 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "退款金额不能为负");
        }
        if (pAmt + gAmt <= 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "退款金额必须大于 0");
        }

        WalWalletTransaction existed = findTxn(WalletBizType.REFUND_IN, cmd.getRefundNo());
        if (existed != null) {
            log.info("退款回冲幂等命中 refundNo={}", cmd.getRefundNo());
            return toResult(existed, true);
        }

        WalWallet wallet = lockWallet(cmd.getBrandId(), cmd.getMemberId());

        Long principalBefore = wallet.getPrincipalBalance();
        Long principalAfter = principalBefore + pAmt;
        Long giftBefore = wallet.getGiftBalance();
        Long giftAfter = giftBefore + gAmt;
        assertNonNegative(principalAfter, giftAfter);

        WalWalletTransaction txn = buildTxn(wallet, cmd.getStoreId(),
                WalletBizType.REFUND_IN, cmd.getRefundNo(),
                pAmt, principalBefore, principalAfter,
                gAmt, giftBefore, giftAfter);
        txn.setOperatorId(cmd.getOperatorId());
        txn.setOperatorType(defaultOperator(cmd.getOperatorType()));
        txn.setRemark(cmd.getRemark());

        insertTxnIdempotent(txn);

        wallet.setPrincipalBalance(principalAfter);
        wallet.setGiftBalance(giftAfter);
        wallet.setTotalRefundIn(wallet.getTotalRefundIn() + pAmt + gAmt);
        updateWallet(wallet);
        evictCache(cmd.getBrandId(), cmd.getMemberId());

        log.info("退款回冲成功 walletId={} refundNo={} 实付+{} 赠送+{}",
                wallet.getId(), cmd.getRefundNo(), pAmt, gAmt);
        return toResult(txn, false);
    }

    // ==================== 充值退款（资金流出） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletChangeResult rechargeRefundOut(Long brandId, Long memberId, Long storeId,
                                                String refundNo, Long principalAmount, Long giftAmount,
                                                String remark, Long operatorId) {
        validateCommon(brandId, memberId, refundNo, "退款单号");
        long pAmt = principalAmount == null ? 0L : principalAmount;
        long gAmt = giftAmount == null ? 0L : giftAmount;
        if (pAmt < 0 || gAmt < 0 || pAmt + gAmt <= 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "退款金额不合法");
        }

        WalWalletTransaction existed = findTxn(WalletBizType.RECHARGE_REFUND_OUT, refundNo);
        if (existed != null) {
            return toResult(existed, true);
        }

        WalWallet wallet = lockWallet(brandId, memberId);
        if (wallet.getPrincipalBalance() < pAmt || wallet.getGiftBalance() < gAmt) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "余额不足，无法扣回充值金额（当前实付 " + wallet.getPrincipalBalance()
                            + " 分 / 赠送 " + wallet.getGiftBalance() + " 分）");
        }

        Long principalBefore = wallet.getPrincipalBalance();
        Long principalAfter = principalBefore - pAmt;
        Long giftBefore = wallet.getGiftBalance();
        Long giftAfter = giftBefore - gAmt;
        assertNonNegative(principalAfter, giftAfter);

        WalWalletTransaction txn = buildTxn(wallet, storeId,
                WalletBizType.RECHARGE_REFUND_OUT, refundNo,
                -pAmt, principalBefore, principalAfter,
                -gAmt, giftBefore, giftAfter);
        txn.setOperatorId(operatorId);
        txn.setOperatorType("STAFF");
        txn.setRemark(remark);

        insertTxnIdempotent(txn);

        wallet.setPrincipalBalance(principalAfter);
        wallet.setGiftBalance(giftAfter);
        updateWallet(wallet);
        evictCache(brandId, memberId);
        return toResult(txn, false);
    }

    // ==================== 人工调整 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletChangeResult adjust(Long brandId, Long memberId, Long storeId, String approvalNo,
                                     Long principalChange, Long giftChange,
                                     String remark, Long operatorId) {
        validateCommon(brandId, memberId, approvalNo, "审批单号");
        long p = principalChange == null ? 0L : principalChange;
        long g = giftChange == null ? 0L : giftChange;
        if (p == 0 && g == 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "调整金额不能同时为 0");
        }

        WalWalletTransaction existed = findTxn(WalletBizType.ADMIN_ADJUST, approvalNo);
        if (existed != null) {
            return toResult(existed, true);
        }

        WalWallet wallet = lockWallet(brandId, memberId);
        Long principalBefore = wallet.getPrincipalBalance();
        Long principalAfter = principalBefore + p;
        Long giftBefore = wallet.getGiftBalance();
        Long giftAfter = giftBefore + g;
        assertNonNegative(principalAfter, giftAfter);

        WalWalletTransaction txn = buildTxn(wallet, storeId,
                WalletBizType.ADMIN_ADJUST, approvalNo,
                p, principalBefore, principalAfter,
                g, giftBefore, giftAfter);
        txn.setOperatorId(operatorId);
        txn.setOperatorType("STAFF");
        txn.setRemark(remark);

        insertTxnIdempotent(txn);

        wallet.setPrincipalBalance(principalAfter);
        wallet.setGiftBalance(giftAfter);
        updateWallet(wallet);
        evictCache(brandId, memberId);
        return toResult(txn, false);
    }

    @Override
    public Object getRechargeOrder(String rechargeNo) {
        return rechargeOrderMapper.selectOne(Wrappers.<WalRechargeOrder>lambdaQuery()
                .eq(WalRechargeOrder::getRechargeNo, rechargeNo));
    }

    // ==================== 内部工具 ====================

    private void validateCommon(Long brandId, Long memberId, String bizNo, String bizNoLabel) {
        if (brandId == null || memberId == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "品牌与会员不能为空");
        }
        if (bizNo == null || bizNo.isBlank()) {
            throw new BizException(ResultCode.PARAM_INVALID, bizNoLabel + "不能为空（幂等键）");
        }
    }

    /**
     * 加行锁读取钱包；不存在时创建。
     */
    private WalWallet lockWallet(Long brandId, Long memberId) {
        WalWallet wallet = walletMapper.selectForUpdate(brandId, memberId);
        if (wallet == null) {
            getOrCreateWallet(brandId, memberId);
            wallet = walletMapper.selectForUpdate(brandId, memberId);
        }
        if (wallet == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "钱包创建失败");
        }
        return wallet;
    }

    private WalWalletTransaction findTxn(WalletBizType type, String bizNo) {
        return txnMapper.selectByBiz(type.name(), bizNo);
    }

    private WalWalletTransaction buildTxn(WalWallet wallet, Long storeId,
                                          WalletBizType type, String bizNo,
                                          long pChange, long pBefore, long pAfter,
                                          long gChange, long gBefore, long gAfter) {
        WalWalletTransaction txn = new WalWalletTransaction();
        txn.setBrandId(wallet.getBrandId());
        txn.setWalletId(wallet.getId());
        txn.setMemberId(wallet.getMemberId());
        txn.setStoreId(storeId);
        txn.setTxnNo(BizNoGenerator.walletTxnNo());
        txn.setBizType(type.name());
        txn.setBizNo(bizNo);
        txn.setPrincipalChange(pChange);
        txn.setPrincipalBefore(pBefore);
        txn.setPrincipalAfter(pAfter);
        txn.setGiftChange(gChange);
        txn.setGiftBefore(gBefore);
        txn.setGiftAfter(gAfter);
        txn.setTraceId(TraceIdHolder.get());
        return txn;
    }

    /**
     * 插入流水。唯一索引冲突视为并发重复请求，转为幂等命中。
     */
    private void insertTxnIdempotent(WalWalletTransaction txn) {
        try {
            txnMapper.insert(txn);
        } catch (DuplicateKeyException e) {
            WalWalletTransaction existed = findTxn(WalletBizType.valueOf(txn.getBizType()), txn.getBizNo());
            if (existed == null) {
                throw e;
            }
            log.warn("流水唯一键冲突，按幂等处理 bizType={} bizNo={}", txn.getBizType(), txn.getBizNo());
            txn.setId(existed.getId());
            txn.setTxnNo(existed.getTxnNo());
        }
    }

    private void updateWallet(WalWallet wallet) {
        // version 由 MyBatis-Plus @Version 插件递增，此处不得手工 +1，
        // 否则插件会额外拼接 AND version=? 条件导致参数缺失。
        int rows = walletMapper.updateById(wallet);
        if (rows != 1) {
            // 持有行锁的情况下不应发生；作为最后防线防止静默失败
            throw new BizException(ResultCode.SYSTEM_ERROR, "钱包余额更新失败");
        }
    }

    private void assertNonNegative(long principal, long gift) {
        if (principal < 0 || gift < 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "余额不能为负数");
        }
    }

    private String defaultOperator(String type) {
        return type == null || type.isBlank() ? "SYSTEM" : type;
    }

    /**
     * 余额变动后失效钱包缓存。
     */
    private void evictCache(Long brandId, Long memberId) {
        try {
            hotDataCache.evictWallet(memberId);
        } catch (Exception e) {
            // 缓存失效失败不影响资金主流程，余额以数据库为准
            log.warn("钱包缓存失效失败（不影响资金一致性）: memberId={}", memberId, e);
        }
    }

    private WalletChangeResult toResult(WalWalletTransaction txn, boolean idempotentHit) {
        return WalletChangeResult.builder()
                .walletId(txn.getWalletId())
                .txnId(txn.getId())
                .txnNo(txn.getTxnNo())
                .principalChange(txn.getPrincipalChange())
                .giftChange(txn.getGiftChange())
                .principalBefore(txn.getPrincipalBefore())
                .principalAfter(txn.getPrincipalAfter())
                .giftBefore(txn.getGiftBefore())
                .giftAfter(txn.getGiftAfter())
                .idempotentHit(idempotentHit)
                .build();
    }
}
