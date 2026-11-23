package com.teaclub.cloud.wallet.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.core.util.BizNoGenerator;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.StoreScope;
import com.teaclub.cloud.security.context.UserContext;
import com.teaclub.cloud.wallet.dto.CardRefundApplyRequest;
import com.teaclub.cloud.wallet.dto.CardRefundAuditRequest;
import com.teaclub.cloud.wallet.entity.WalCardRefund;
import com.teaclub.cloud.wallet.entity.WalWallet;
import com.teaclub.cloud.wallet.mapper.WalCardRefundMapper;
import com.teaclub.cloud.wallet.service.CardRefundService;
import com.teaclub.cloud.wallet.service.WalletService;
import com.teaclub.cloud.wallet.vo.CardRefundVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会员退卡实现。
 *
 * <p><b>为什么审核时才扣款</b>：申请期间会员可能继续消费，
 * 若申请时即冻结余额，用户会突然无法消费体验很差；
 * 因此审核通过时再读取当前余额，余额已被消费到不足时
 * <b>按实际剩余金额退</b>（退回当前实付余额），不阻断审核。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardRefundServiceImpl implements CardRefundService {

    private final WalCardRefundMapper cardRefundMapper;
    private final WalletService walletService;

    private final com.teaclub.cloud.wallet.client.BrandInternalClient brandClient;
    private final com.teaclub.cloud.redis.cache.CacheService cacheService;

    /** 退款规则缓存 60 秒，配置变更可较快生效 */
    private static final long RULE_CACHE_SECONDS = 60L;

    /**
     * 会员自助退卡：<b>提交即退款</b>，无需人工审核。
     *
     * <p><b>资金规则</b>：
     * <ul>
     *   <li>实付本金余额全额退回</li>
     *   <li>赠送金额作废，<b>不折现</b>（营销成本不能提现）</li>
     *   <li>退款金额由服务端按当前余额计算，不接受客户端传参</li>
     * </ul>
     *
     * <p><b>风险控制</b>（自助场景必须有的护栏）：
     * <ol>
     *   <li>同一会员每日最多退卡一次（防频繁刷）</li>
     *   <li>无实付余额时直接拒绝，不产生退卡单</li>
     *   <li>扣款走 {@code rechargeRefundOut}：行锁 + 余额校验 + 唯一流水，天然幂等</li>
     *   <li>退卡单与资金流水同事务，任一失败整体回滚</li>
     * </ol>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CardRefundVO apply(Long memberId, CardRefundApplyRequest request) {
        Long brandId = UserContext.require().getBrandId();

        RefundRule rule = loadRule(brandId);

        // 护栏 1：每日退卡次数上限（0 表示不限）
        if (rule.dailyLimit > 0) {
            Long todayCount = cardRefundMapper.selectCount(Wrappers.<WalCardRefund>lambdaQuery()
                    .eq(WalCardRefund::getBrandId, brandId)
                    .eq(WalCardRefund::getMemberId, memberId)
                    .ne(WalCardRefund::getStatus, WalCardRefund.STATUS_REJECTED)
                    .ge(WalCardRefund::getCreateTime, LocalDate.now().atStartOfDay()));
            if (todayCount != null && todayCount >= rule.dailyLimit) {
                throw new BizException(ResultCode.CARD_REFUND_DAILY_LIMIT,
                        "今日已办理 " + todayCount + " 次退卡（上限 " + rule.dailyLimit + " 次），请明日再试");
            }
        }

        WalWallet wallet = walletService.getWallet(brandId, memberId);
        if (wallet == null) {
            throw new BizException(ResultCode.WALLET_NOT_FOUND, "钱包不存在，无法退卡");
        }
        long principal = nz(wallet.getPrincipalBalance());
        long gift = nz(wallet.getGiftBalance());

        // 护栏 2：只有赠送金没有本金时不允许退（退无可退）
        if (principal <= 0) {
            throw new BizException(ResultCode.CARD_REFUND_NO_PRINCIPAL,
                    "当前无实付余额可退（赠送余额 " + fmtYuan(gift) + " 元不可提现）");
        }

        // 护栏 3：赠送金已被消费 → 拒绝自助退卡，转人工处理。
        //
        // 为什么需要这条：按 GIFT_FIRST 扣款顺序，会员可能已把赠送金花掉，
        // 此时退全部实付本金等于用营销费用替用户买单。
        // 规则要求「原路退回实付本金、同时扣回赠送金」——
        // 赠送额度已被消耗说明无法足额扣回，必须人工核实后处理。
        long giftConsumed = nz(wallet.getTotalGiftOut());
        if (giftConsumed > 0 && "REJECT".equals(rule.giftConsumedAction)) {
            throw new BizException(ResultCode.CARD_REFUND_GIFT_USED,
                    "检测到赠送金额已被使用（累计消费赠送 " + fmtYuan(giftConsumed)
                            + " 元），自助退卡仅支持赠送金未动用的情形，请联系门店店长人工处理");
        }
        String refundNo = BizNoGenerator.refundNo();

        WalCardRefund record = new WalCardRefund();
        record.setBrandId(brandId);
        record.setMemberId(memberId);
        record.setRefundNo(BizNoGenerator.of("CR"));
        record.setPrincipalAmount(principal);
        record.setGiftVoidAmount(gift);
        record.setApplyReason(request.getReason());

        // 按品牌规则决定：需要审核 → 只建单不扣款；自助秒退 → 立即扣款
        boolean needAudit = rule.needAudit == 1;
        if (needAudit) {
            record.setStatus(WalCardRefund.STATUS_PENDING);
            cardRefundMapper.insert(record);
            log.info("[退卡申请-待审核] memberId={} refundNo={} principal={} gift={}",
                    memberId, record.getRefundNo(), principal, gift);
            return toVO(record);
        }

        record.setStatus(WalCardRefund.STATUS_APPROVED);
        record.setAuditBy(memberId);
        record.setAuditTime(LocalDateTime.now());
        record.setAuditRemark("会员自助退卡（免审核）");
        record.setRefundTxnNo(refundNo);
        cardRefundMapper.insert(record);

        // 立即扣回：实付本金退回、赠送金作废
        walletService.rechargeRefundOut(brandId, memberId, null, refundNo, principal, gift,
                "会员自助退卡：" + request.getReason(), null);

        log.info("[自助退卡-秒退] memberId={} 退本金={} 作废赠送={} refundNo={}",
                memberId, principal, gift, refundNo);
        return toVO(record);
    }

    @Override
    public List<CardRefundVO> myApplications(Long memberId) {
        Long brandId = UserContext.require().getBrandId();
        return cardRefundMapper.selectList(Wrappers.<WalCardRefund>lambdaQuery()
                        .eq(WalCardRefund::getBrandId, brandId)
                        .eq(WalCardRefund::getMemberId, memberId)
                        .orderByDesc(WalCardRefund::getId)
                        .last("LIMIT 20"))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public PageResult<CardRefundVO> page(Integer pageNo, Integer pageSize, String status, Long memberId) {
        LoginUser current = UserContext.require();
        StoreScope.from(current);  // 退卡记录不含门店维度，此处仅校验登录态

        Page<WalCardRefund> page = new Page<>(
                pageNo == null || pageNo < 1 ? 1 : pageNo,
                pageSize == null || pageSize < 1 ? 20 : pageSize);
        Page<WalCardRefund> result = cardRefundMapper.selectPage(page,
                Wrappers.<WalCardRefund>lambdaQuery()
                        .eq(WalCardRefund::getBrandId, current.getBrandId())
                        .eq(status != null && !status.isBlank(), WalCardRefund::getStatus, status)
                        .eq(memberId != null, WalCardRefund::getMemberId, memberId)
                        .orderByDesc(WalCardRefund::getId));
        List<CardRefundVO> records = result.getRecords().stream()
                .map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CardRefundVO audit(Long id, CardRefundAuditRequest request) {
        LoginUser current = UserContext.require();
        WalCardRefund record = cardRefundMapper.selectById(id);
        if (record == null || !record.getBrandId().equals(current.getBrandId())) {
            throw new BizException(ResultCode.CARD_REFUND_NOT_FOUND, "退卡申请不存在");
        }
        if (!WalCardRefund.STATUS_PENDING.equals(record.getStatus())) {
            throw new BizException(ResultCode.CARD_REFUND_ALREADY_HANDLED, "该申请已处理，请勿重复审核");
        }

        String target = request.getStatus();
        if (!WalCardRefund.STATUS_APPROVED.equals(target)
                && !WalCardRefund.STATUS_REJECTED.equals(target)) {
            throw new BizException(ResultCode.PARAM_INVALID, "审核结果只能是 APPROVED 或 REJECTED");
        }

        String refundNo = request.getRefundNo();
        if (refundNo == null || refundNo.isBlank()) {
            refundNo = BizNoGenerator.refundNo();
        }

        if (WalCardRefund.STATUS_APPROVED.equals(target)) {
            // 通过：以「当前余额」为准扣回（申请后可能又消费了）
            WalWallet wallet = walletService.getWallet(record.getBrandId(), record.getMemberId());
            long principal = nz(wallet.getPrincipalBalance());
            long gift = nz(wallet.getGiftBalance());
            if (principal + gift <= 0) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "会员当前余额已为 0，无法退款，请驳回该申请");
            }
            // 实付本金退回，赠送金作废（不折现）
            walletService.rechargeRefundOut(record.getBrandId(), record.getMemberId(), null,
                    refundNo, principal, gift,
                    "会员退卡：" + (request.getRemark() == null ? "" : request.getRemark()),
                    current.getUserId());
            record.setRefundTxnNo(refundNo);
            record.setPrincipalAmount(principal);
            record.setGiftVoidAmount(gift);
        }

        record.setStatus(target);
        record.setAuditBy(current.getUserId());
        record.setAuditTime(LocalDateTime.now());
        record.setAuditRemark(request.getRemark());
        cardRefundMapper.updateById(record);

        log.info("[退卡审核] refundNo={} status={} auditor={} principal={} giftVoid={}",
                record.getRefundNo(), target, current.getUserId(),
                record.getPrincipalAmount(), record.getGiftVoidAmount());
        return toVO(record);
    }

    /**
     * 读取品牌退款规则。
     *
     * <p><b>降级策略</b>：auth-service 不可用时回退到最保守的默认值
     * （需审核、每日 1 次、赠送已用则拒绝），保证资金安全优先。
     */
    RefundRule loadRule(Long brandId) {
        String cacheKey = "teaclub:brand:" + brandId + ":refund-rule";
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> cached = cacheService.get(cacheKey, Map.class);
            if (cached != null && !cached.isEmpty()) {
                return RefundRule.of(cached);
            }
        } catch (Exception e) {
            log.warn("读取退款规则缓存失败，继续查库: brandId={}", brandId, e);
        }

        try {
            var resp = brandClient.getRefundRule(brandId);
            if (resp != null && resp.getCode() == 0 && resp.getData() != null) {
                Map<String, Object> data = resp.getData();
                try {
                    cacheService.set(cacheKey, data, java.time.Duration.ofSeconds(RULE_CACHE_SECONDS));
                } catch (Exception e) {
                    log.warn("写入退款规则缓存失败: brandId={}", brandId, e);
                }
                return RefundRule.of(data);
            }
            log.warn("获取品牌退款规则失败，使用保守默认值: brandId={} resp={}", brandId, resp);
        } catch (Exception e) {
            log.error("调用 auth-service 获取退款规则异常，使用保守默认值: brandId={}", brandId, e);
        }
        return RefundRule.defaults();
    }

    /** 品牌退款规则快照。 */
    static final class RefundRule {
        final int needAudit;
        final int dailyLimit;
        final String giftConsumedAction;

        RefundRule(int needAudit, int dailyLimit, String giftConsumedAction) {
            this.needAudit = needAudit;
            this.dailyLimit = dailyLimit;
            this.giftConsumedAction = giftConsumedAction;
        }

        static RefundRule of(Map<String, Object> m) {
            return new RefundRule(
                    toInt(m.get("needAudit"), 1),
                    toInt(m.get("dailyLimit"), 1),
                    m.get("giftConsumedAction") == null ? "REJECT" : String.valueOf(m.get("giftConsumedAction")));
        }

        /** 保守默认：需审核、每日 1 次、赠送已用则拒绝 */
        static RefundRule defaults() {
            return new RefundRule(1, 1, "REJECT");
        }

        private static int toInt(Object v, int def) {
            if (v instanceof Number n) {
                return n.intValue();
            }
            if (v != null) {
                try {
                    return Integer.parseInt(String.valueOf(v));
                } catch (NumberFormatException ignored) {
                    return def;
                }
            }
            return def;
        }
    }
    private CardRefundVO toVO(WalCardRefund r) {
        CardRefundVO vo = new CardRefundVO();
        vo.setId(r.getId());
        vo.setRefundNo(r.getRefundNo());
        vo.setMemberId(r.getMemberId());
        vo.setPrincipalAmount(r.getPrincipalAmount());
        vo.setGiftVoidAmount(r.getGiftVoidAmount());
        vo.setStatus(r.getStatus());
        vo.setApplyReason(r.getApplyReason());
        vo.setAuditBy(r.getAuditBy());
        vo.setAuditTime(r.getAuditTime());
        vo.setAuditRemark(r.getAuditRemark());
        vo.setRefundTxnNo(r.getRefundTxnNo());
        vo.setCreateTime(r.getCreateTime());
        return vo;
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    /** 分 → 元（保留两位），仅用于提示文案 */
    private static String fmtYuan(long fen) {
        return String.format("%.2f", fen / 100.0);
    }
}