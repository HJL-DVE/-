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
import com.teaclub.cloud.wallet.entity.WalWalletFreeze;
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
import java.util.LinkedHashMap;
import java.util.Map;

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
    private final com.teaclub.cloud.wallet.mapper.WalWalletFreezeMapper freezeMapper;
    private final com.teaclub.cloud.messaging.outbox.OutboxService outboxService;

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
        {
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("rechargeNo", cmd.getRechargeNo());
            ev.put("walletId", wallet.getId());
            ev.put("memberId", cmd.getMemberId());
            ev.put("payAmount", cmd.getPayAmount());
            ev.put("giftAmount", giftAmount);
            ev.put("principalAfter", principalAfter);
            ev.put("giftAfter", giftAfter);
            ev.put("txnNo", txn.getTxnNo());
            outboxService.enqueue(com.teaclub.cloud.messaging.event.DomainEvents.RECHARGE_PAID,
                    cmd.getRechargeNo(), ev);
        }
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
        {
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("orderNo", cmd.getOrderNo());
            ev.put("walletId", wallet.getId());
            ev.put("memberId", cmd.getMemberId());
            ev.put("payableAmount", payable);
            ev.put("principalDeduct", principalDeduct);
            ev.put("giftDeduct", giftDeduct);
            ev.put("principalAfter", principalAfter);
            ev.put("giftAfter", giftAfter);
            ev.put("txnNo", txn.getTxnNo());
            outboxService.enqueue(com.teaclub.cloud.messaging.event.DomainEvents.WALLET_DEDUCTED,
                    cmd.getOrderNo(), ev);
        }
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


    // ==================== 赠送金到期核销 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletChangeResult expireGift(Long rechargeTxnId) {
        if (rechargeTxnId == null) {
            return null;
        }
        WalWalletTransaction src = txnMapper.selectById(rechargeTxnId);
        if (src == null
                || !WalletBizType.RECHARGE_IN.name().equals(src.getBizType())
                || src.getGiftAmount() == null || src.getGiftAmount() <= 0
                || src.getGiftExpireTime() == null
                || src.getGiftExpireTime().isAfter(LocalDateTime.now())) {
            return null;
        }

        String bizNo = "GIFT-EXPIRE-" + rechargeTxnId;
        WalWalletTransaction existed = findTxn(WalletBizType.GIFT_EXPIRE, bizNo);
        if (existed != null) {
            return toResult(existed, true);
        }

        // 与消费扣款共用钱包行锁，避免并发扣成负数；
        // 同一充值流水的重复核销也会在锁内被上面的幂等检查拦住。
        WalWallet wallet = lockWallet(src.getBrandId(), src.getMemberId());
        long before = wallet.getGiftBalance() == null ? 0L : wallet.getGiftBalance();
        long deduct = Math.min(src.getGiftAmount(), before);
        long after = before - deduct;
        assertNonNegative(wallet.getPrincipalBalance(), after);

        WalWalletTransaction txn = buildTxn(wallet, src.getStoreId(),
                WalletBizType.GIFT_EXPIRE, bizNo,
                0L, wallet.getPrincipalBalance(), wallet.getPrincipalBalance(),
                -deduct, before, after);
        txn.setRemark("赠送金到期核销（充值流水 " + src.getId() + "）");
        txn.setOperatorType("SYSTEM");

        insertTxnIdempotent(txn);

        wallet.setGiftBalance(after);
        wallet.setTotalGiftOut((wallet.getTotalGiftOut() == null ? 0L : wallet.getTotalGiftOut()) + deduct);
        updateWallet(wallet);
        evictCache(wallet.getBrandId(), wallet.getMemberId());

        log.info("赠送金到期核销 walletId={} 充值流水={} 核销={}分 赠送余额 {} -> {}",
                wallet.getId(), src.getId(), deduct, before, after);
        return toResult(txn, false);
    }


    // ==================== 余额冻结 / 确认 / 释放 ====================

    /**
     * 冻结余额。
     *
     * <p><b>不修改账户余额</b>：只写 wal_wallet_freeze 记录。
     * 可用余额 = 账户余额 - SUM(FROZEN 冻结额)。
     * 这样"余额 = 流水累计"不变量始终成立（冻结时钱没动，不写流水）。
     *
     * <p>冻结前校验：可用余额是否足够；幂等：同一 orderNo 只能冻结一次。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long freeze(Long brandId, Long memberId, String orderNo,
                       Long freezePrincipal, Long freezeGift, Integer expireMinutes) {
        validateCommon(brandId, memberId, orderNo, "订单号");
        long fp = freezePrincipal == null ? 0L : freezePrincipal;
        long fg = freezeGift == null ? 0L : freezeGift;
        if (fp < 0 || fg < 0 || fp + fg <= 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "冻结金额必须大于 0");
        }

        // 幂等：同一订单号重复冻结直接返回原记录
        WalWalletFreeze existed = freezeMapper.selectByOrderNo(orderNo);
        if (existed != null) {
            log.info("冻结幂等命中 orderNo={} freezeId={}", orderNo, existed.getId());
            return existed.getId();
        }

        WalWallet wallet = walletMapper.selectOne(Wrappers.<WalWallet>lambdaQuery()
                .eq(WalWallet::getBrandId, brandId)
                .eq(WalWallet::getMemberId, memberId));
        if (wallet == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "钱包不存在");
        }
        if (!"NORMAL".equals(wallet.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "钱包已被冻结，无法再次冻结扣款");
        }

        // 校验可用余额：账户余额 - 已冻结
        Map<String, Object> frozen = freezeMapper.sumFrozen(wallet.getId());
        long availP = wallet.getPrincipalBalance() - toLong(frozen.get("principal"));
        long availG = wallet.getGiftBalance() - toLong(frozen.get("gift"));
        if (availP < fp || availG < fg) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "可用余额不足：实付可用 " + availP + " 分需 " + fp
                            + "；赠送可用 " + availG + " 分需 " + fg);
        }

        WalWalletFreeze record = new WalWalletFreeze();
        record.setBrandId(brandId);
        record.setWalletId(wallet.getId());
        record.setMemberId(memberId);
        record.setOrderNo(orderNo);
        record.setFreezePrincipal(fp);
        record.setFreezeGift(fg);
        record.setStatus("FROZEN");
        record.setExpireTime(expireMinutes == null || expireMinutes <= 0
                ? null : LocalDateTime.now().plusMinutes(expireMinutes));
        try {
            freezeMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 并发重复冻结：唯一索引兜底
            WalWalletFreeze concurrent = freezeMapper.selectByOrderNo(orderNo);
            if (concurrent == null) throw e;
            log.warn("冻结唯一键冲突，按幂等处理 orderNo={}", orderNo);
            return concurrent.getId();
        }
        evictCache(brandId, memberId);
        log.info("冻结成功 orderNo={} 实付={} 赠送={} 可用余额({},{})",
                orderNo, fp, fg, availP - fp, availG - fg);
        {
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("orderNo", orderNo);
            ev.put("walletId", wallet.getId());
            ev.put("memberId", memberId);
            ev.put("freezePrincipal", fp);
            ev.put("freezeGift", fg);
            ev.put("freezeId", record.getId());
            outboxService.enqueue(com.teaclub.cloud.messaging.event.DomainEvents.WALLET_FROZEN,
                    orderNo, ev);
        }
        return record.getId();
    }

    @Override
    public Map<String, Object> getAvailableBalance(Long brandId, Long memberId) {
        WalWallet w = getWallet(brandId, memberId);
        Map<String, Object> frozen = freezeMapper.sumFrozen(w.getId());
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("principalBalance", w.getPrincipalBalance());
        result.put("giftBalance", w.getGiftBalance());
        result.put("frozenPrincipal", toLong(frozen.get("principal")));
        result.put("frozenGift", toLong(frozen.get("gift")));
        result.put("availablePrincipal", w.getPrincipalBalance() - toLong(frozen.get("principal")));
        result.put("availableGift", w.getGiftBalance() - toLong(frozen.get("gift")));
        return result;
    }

    /**
     * 确认冻结扣款。
     *
     * <p>先把冻结记录标记为 CONFIRMED（防止重复确认），
     * 再走原有的 consumeOut 写资金流水 —— 保证"钱真的动了"时才写流水。
     * 若 consumeOut 失败，事务回滚，冻结记录恢复 FROZEN，可重试或释放。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletChangeResult confirmFreeze(Long brandId, Long memberId, String orderNo,
                                             Long storeId, Long operatorId) {
        WalWalletFreeze freeze = freezeMapper.selectByOrderNo(orderNo);
        if (freeze == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "冻结记录不存在: " + orderNo);
        }
        if ("CONFIRMED".equals(freeze.getStatus())) {
            // 幂等：已确认，查原流水返回
            WalWalletTransaction txn = txnMapper.selectByBiz(WalletBizType.CONSUME_OUT.name(), orderNo);
            if (txn != null) {
                return toResult(txn, true);
            }
        }
        if (!"FROZEN".equals(freeze.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "冻结记录状态为 " + freeze.getStatus() + "，不可确认扣款");
        }

        // 按冻结时的精确构成扣款：实付扣 freezePrincipal，赠送扣 freezeGift
        // 不使用 consumeOut 的 deductOrder 推断 —— 冻结已经锁定了资金构成
        WalletChangeResult result = consumeOutExact(
                brandId, memberId, storeId, orderNo,
                freeze.getFreezePrincipal(), freeze.getFreezeGift(), operatorId);

        WalWalletFreeze patch = new WalWalletFreeze();
        patch.setId(freeze.getId());
        patch.setStatus("CONFIRMED");
        patch.setConfirmedTxnNo(result.getTxnNo());
        freezeMapper.updateById(patch);
        evictCache(brandId, memberId);
        log.info("冻结确认扣款成功 orderNo={} txnNo={}", orderNo, result.getTxnNo());
        return result;
    }

    /**
     * 释放冻结（失败订单/超时订单）。
     *
     * <p>仅改冻结记录状态为 RELEASED，<b>不写流水</b>（钱没动）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletChangeResult confirmFreezePartial(Long brandId, Long memberId, String orderNo,
                                                   Long storeId, Long operatorId,
                                                   Long principal, Long gift) {
        WalWalletFreeze freeze = freezeMapper.selectByOrderNo(orderNo);
        if (freeze == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "冻结记录不存在: " + orderNo);
        }
        if ("CONFIRMED".equals(freeze.getStatus())) {
            WalWalletTransaction txn = txnMapper.selectByBiz(WalletBizType.CONSUME_OUT.name(), orderNo);
            if (txn != null) {
                return toResult(txn, true);
            }
        }
        if (!"FROZEN".equals(freeze.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "冻结记录状态为 " + freeze.getStatus() + "，不可确认扣款");
        }

        long p = principal == null ? freeze.getFreezePrincipal() : principal;
        long g = gift == null ? freeze.getFreezeGift() : gift;
        if (p < 0 || g < 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "确认扣款金额不能为负");
        }
        if (p > freeze.getFreezePrincipal() || g > freeze.getFreezeGift()) {
            throw new BizException(ResultCode.PARAM_INVALID,
                    "确认扣款金额超过冻结额：冻结 " + freeze.getFreezePrincipal() + "/" + freeze.getFreezeGift()
                            + "，申请 " + p + "/" + g);
        }
        if (p + g <= 0) {
            throw new BizException(ResultCode.PARAM_INVALID,
                    "确认扣款金额为 0，如需取消请调用释放冻结");
        }

        // 按本次确认额扣款，剩余冻结额随状态变更一并释放
        WalletChangeResult result = consumeOutExact(brandId, memberId, storeId, orderNo, p, g, operatorId);

        WalWalletFreeze patch = new WalWalletFreeze();
        patch.setId(freeze.getId());
        patch.setStatus("CONFIRMED");
        patch.setConfirmedTxnNo(result.getTxnNo());
        patch.setFreezePrincipal(p);
        patch.setFreezeGift(g);
        long releasedP = freeze.getFreezePrincipal() - p;
        long releasedG = freeze.getFreezeGift() - g;
        if (releasedP > 0 || releasedG > 0) {
            patch.setRemark("部分确认：释放 " + releasedP + "/" + releasedG);
        }
        freezeMapper.updateById(patch);
        evictCache(brandId, memberId);
        log.info("冻结部分确认 orderNo={} 扣款({},{}) 释放({},{}) txnNo={}",
                orderNo, p, g, releasedP, releasedG, result.getTxnNo());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean releaseFreeze(Long brandId, Long memberId, String orderNo, String reason) {
        WalWalletFreeze freeze = freezeMapper.selectByOrderNo(orderNo);
        if (freeze == null) {
            log.warn("释放冻结：记录不存在 orderNo={}", orderNo);
            return false;
        }
        if (!"FROZEN".equals(freeze.getStatus())) {
            log.warn("释放冻结：状态非 FROZEN orderNo={} status={}", orderNo, freeze.getStatus());
            return false;
        }
        WalWalletFreeze patch = new WalWalletFreeze();
        patch.setId(freeze.getId());
        patch.setStatus("RELEASED");
        patch.setRemark(reason);
        freezeMapper.updateById(patch);
        evictCache(brandId, memberId);
        log.info("冻结已释放 orderNo={} reason={}", orderNo, reason);
        return true;
    }

    /**
     * 按指定构成精确扣款（供冻结确认使用）。
     *
     * <p>与 consumeOut 共用同一套事务/行锁/幂等/流水机制，
     * 区别仅在于扣款额由冻结记录显式指定，而非按 deductOrder 推断。
     */
    private WalletChangeResult consumeOutExact(Long brandId, Long memberId, Long storeId,
                                               String orderNo, long pDeduct, long gDeduct,
                                               Long operatorId) {
        // 幂等前置检查
        WalWalletTransaction existed = findTxn(WalletBizType.CONSUME_OUT, orderNo);
        if (existed != null) {
            return toResult(existed, true);
        }

        WalWallet wallet = lockWallet(brandId, memberId);
        if (!"NORMAL".equals(wallet.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "钱包已被冻结，无法消费");
        }
        if (wallet.getPrincipalBalance() < pDeduct || wallet.getGiftBalance() < gDeduct) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "余额不足：实付 " + wallet.getPrincipalBalance() + " 需 " + pDeduct
                            + "；赠送 " + wallet.getGiftBalance() + " 需 " + gDeduct);
        }

        Long pBefore = wallet.getPrincipalBalance();
        Long gBefore = wallet.getGiftBalance();
        Long pAfter = pBefore - pDeduct;
        Long gAfter = gBefore - gDeduct;
        assertNonNegative(pAfter, gAfter);

        WalWalletTransaction txn = buildTxn(wallet, storeId,
                WalletBizType.CONSUME_OUT, orderNo,
                -pDeduct, pBefore, pAfter, -gDeduct, gBefore, gAfter);
        txn.setOperatorId(operatorId);
        txn.setOperatorType("STAFF");
        txn.setRemark("冻结确认扣款（实付 " + pDeduct + " + 赠送 " + gDeduct + "）");
        insertTxnIdempotent(txn);

        wallet.setPrincipalBalance(pAfter);
        wallet.setGiftBalance(gAfter);
        wallet.setTotalPrincipalOut(wallet.getTotalPrincipalOut() + pDeduct);
        wallet.setTotalGiftOut(wallet.getTotalGiftOut() + gDeduct);
        updateWallet(wallet);
        {
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("orderNo", orderNo);
            ev.put("walletId", wallet.getId());
            ev.put("memberId", memberId);
            ev.put("principalDeduct", pDeduct);
            ev.put("giftDeduct", gDeduct);
            ev.put("principalAfter", pAfter);
            ev.put("giftAfter", gAfter);
            ev.put("txnNo", txn.getTxnNo());
            ev.put("source", "freeze-confirm");
            outboxService.enqueue(com.teaclub.cloud.messaging.event.DomainEvents.WALLET_DEDUCTED,
                    orderNo, ev);
        }
        return toResult(txn, false);
    }

    private static long toLong(Object v) {
        return v == null ? 0L : ((Number) v).longValue();
    }

    // ==================== 充值订单 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createRechargeOrder(Long brandId, Long memberId, Long storeId,
                                      Long rechargeAmount, Long giftAmount, Integer giftExpireDays) {
        if (brandId == null || memberId == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "品牌与会员不能为空");
        }
        if (rechargeAmount == null || rechargeAmount <= 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "充值金额必须大于 0");
        }
        long gift = giftAmount == null ? 0L : giftAmount;
        if (gift < 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "赠送金额不能为负");
        }

        WalRechargeOrder order = new WalRechargeOrder();
        order.setBrandId(brandId);
        order.setRechargeNo(BizNoGenerator.rechargeNo());
        order.setMemberId(memberId);
        order.setStoreId(storeId);
        order.setRechargeAmount(rechargeAmount);
        order.setGiftAmount(gift);
        order.setGiftExpireDays(giftExpireDays == null ? 0 : giftExpireDays);
        order.setStatus("PENDING");
        rechargeOrderMapper.insert(order);
        log.info("创建充值单 rechargeNo={} 实付={} 赠送={}", order.getRechargeNo(), rechargeAmount, gift);
        return order.getRechargeNo();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletChangeResult processRechargePaid(String rechargeNo, String paymentNo) {
        WalRechargeOrder order = rechargeOrderMapper.selectByNoForUpdate(rechargeNo);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "充值单不存在: " + rechargeNo);
        }

        // 幂等：已入账直接返回原流水
        if ("PAID".equals(order.getStatus())) {
            WalWalletTransaction existed = findTxn(WalletBizType.RECHARGE_IN, rechargeNo);
            log.info("充值单已入账，幂等返回 rechargeNo={}", rechargeNo);
            return existed == null ? null : toResult(existed, true);
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "充值单状态为 " + order.getStatus() + "，不可入账");
        }

        // 入账（rechargeIn 内含幂等与行锁）
        WalletChangeResult result = rechargeIn(RechargeInCmd.builder()
                .brandId(order.getBrandId())
                .memberId(order.getMemberId())
                .storeId(order.getStoreId())
                .rechargeNo(rechargeNo)
                .payAmount(order.getRechargeAmount())
                .giftAmount(order.getGiftAmount())
                .giftExpireDays(order.getGiftExpireDays())
                .operatorType("SYSTEM")
                .build());

        WalRechargeOrder patch = new WalRechargeOrder();
        patch.setId(order.getId());
        patch.setStatus("PAID");
        patch.setPaymentNo(paymentNo);
        patch.setPayTime(LocalDateTime.now());
        rechargeOrderMapper.updateById(patch);
        log.info("充值单入账完成 rechargeNo={} paymentNo={}", rechargeNo, paymentNo);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRechargeRefunded(String rechargeNo, String refundNo) {
        WalRechargeOrder order = rechargeOrderMapper.selectByNoForUpdate(rechargeNo);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "充值单不存在: " + rechargeNo);
        }
        if ("REFUNDED".equals(order.getStatus())) {
            log.info("充值单已标记退款，忽略重复请求 rechargeNo={} refundNo={}", rechargeNo, refundNo);
            return;
        }
        if (!"PAID".equals(order.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "充值单状态为 " + order.getStatus() + "，不可标记退款");
        }
        // 账实校验：没有扣回流水就不允许把充值单改成已退款
        if (!existsTxn(WalletBizType.RECHARGE_REFUND_OUT.name(), refundNo)) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "退款扣回流水不存在，拒绝标记充值单已退款 refundNo=" + refundNo);
        }

        WalRechargeOrder patch = new WalRechargeOrder();
        patch.setId(order.getId());
        patch.setStatus("REFUNDED");
        String oldRemark = order.getRemark();
        patch.setRemark("退卡退款 " + refundNo
                + (oldRemark == null || oldRemark.isBlank() ? "" : "；" + oldRemark));
        rechargeOrderMapper.updateById(patch);
        log.info("充值单已标记退款 rechargeNo={} refundNo={}", rechargeNo, refundNo);
    }

    @Override
    public boolean existsTxn(String bizType, String bizNo) {
        if (bizType == null || bizNo == null) {
            return false;
        }
        try {
            return txnMapper.selectByBiz(bizType, bizNo) != null;
        } catch (Exception e) {
            return false;
        }
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
