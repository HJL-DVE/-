package com.teaclub.cloud.marketing.service.impl;

import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.core.util.BizNoGenerator;
import com.teaclub.cloud.marketing.entity.MktPointsAccount;
import com.teaclub.cloud.marketing.entity.MktPointsBatch;
import com.teaclub.cloud.marketing.entity.MktPointsRule;
import com.teaclub.cloud.marketing.entity.MktPointsTransaction;
import com.teaclub.cloud.marketing.entity.MktSignIn;
import com.teaclub.cloud.marketing.enums.PointsBizType;
import com.teaclub.cloud.marketing.mapper.MktPointsAccountMapper;
import com.teaclub.cloud.marketing.mapper.MktPointsBatchMapper;
import com.teaclub.cloud.marketing.mapper.MktPointsRuleMapper;
import com.teaclub.cloud.marketing.mapper.MktPointsTransactionMapper;
import com.teaclub.cloud.marketing.mapper.MktSignInMapper;
import com.teaclub.cloud.marketing.service.PointsService;
import com.teaclub.cloud.marketing.vo.PointsAccountVO;
import com.teaclub.cloud.marketing.vo.PointsRuleVO;
import com.teaclub.cloud.marketing.vo.PointsTransactionVO;
import com.teaclub.cloud.marketing.vo.SignInResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 积分服务实现。
 *
 * <p><b>发积分的关键顺序</b>：
 * <ol>
 *   <li>先 INSERT 流水（唯一索引在这里发挥作用）→ 重复投递立刻失败</li>
 *   <li>再加账户余额</li>
 *   <li>最后落积分批次（供过期处理）</li>
 * </ol>
 * 三者同一事务，任何一步失败整体回滚，不会出现"扣了批次没加余额"这种脏状态。
 *
 * <p><b>为什么不用 Redis 去重</b>：
 * 积分是钱，Redis 丢 key 就会重复发。唯一索引是数据库级保证，重启也不失效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsServiceImpl implements PointsService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long DEFAULT_EXPIRE_DAYS = 365L;

    private final MktPointsAccountMapper accountMapper;
    private final com.teaclub.cloud.marketing.service.LevelBenefitService levelBenefitService;
    private final com.teaclub.cloud.marketing.service.ActivityService activityService;
    private final MktPointsTransactionMapper txnMapper;
    private final MktPointsBatchMapper batchMapper;
    private final MktPointsRuleMapper ruleMapper;
    private final MktSignInMapper signInMapper;
    private final com.teaclub.cloud.marketing.client.MemberSnapshotClient memberSnapshotClient;

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public PointsRuleVO updateRule(Long id, com.teaclub.cloud.marketing.dto.PointsRuleUpdateRequest request) {
        MktPointsRule rule = ruleMapper.selectById(id);
        if (rule == null || Integer.valueOf(1).equals(rule.getDeleted())) {
            throw new BizException(ResultCode.NOT_FOUND, "积分规则不存在");
        }
        if (!rule.getBrandId().equals(brandId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权修改其它品牌的积分规则");
        }
        // 数值合法性：避免除零与负值
        if (request.getAmountPerPoint() != null) {
            if (request.getAmountPerPoint() <= 0) {
                throw new BizException(ResultCode.PARAM_INVALID, "多少金额得1积分必须大于0");
            }
            rule.setAmountPerPoint(request.getAmountPerPoint());
        }
        if (request.getSignInPoints() != null) {
            if (request.getSignInPoints() < 0) {
                throw new BizException(ResultCode.PARAM_INVALID, "签到积分不能为负");
            }
            rule.setSignInPoints(request.getSignInPoints());
        }
        if (request.getSignInContinuousMax() != null) {
            if (request.getSignInContinuousMax() < 0) {
                throw new BizException(ResultCode.PARAM_INVALID, "连续签到上限不能为负");
            }
            rule.setSignInContinuousMax(request.getSignInContinuousMax());
        }
        if (request.getSignInBonus() != null) {
            if (request.getSignInBonus() < 0) {
                throw new BizException(ResultCode.PARAM_INVALID, "连续签到加成不能为负");
            }
            rule.setSignInBonus(request.getSignInBonus());
        }
        if (request.getExpireDays() != null) {
            if (request.getExpireDays() < 0) {
                throw new BizException(ResultCode.PARAM_INVALID, "有效期天数不能为负");
            }
            rule.setExpireDays(request.getExpireDays());
        }
        if (request.getStatus() != null) {
            if (!"ENABLED".equals(request.getStatus()) && !"DISABLED".equals(request.getStatus())) {
                throw new BizException(ResultCode.PARAM_INVALID, "状态只能是 ENABLED 或 DISABLED");
            }
            rule.setStatus(request.getStatus());
        }
        rule.setUpdateTime(java.time.LocalDateTime.now());
        ruleMapper.updateById(rule);
        log.info("[POINTS-RULE] 更新规则 id={} code={} amountPerPoint={} signInPoints={}",
                id, rule.getRuleCode(), rule.getAmountPerPoint(), rule.getSignInPoints());
        return listRules().stream().filter(v -> id.equals(v.getId())).findFirst()
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "规则不存在"));
    }

    // ==================== 账户 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PointsAccountVO account(Long memberId) {
        Long brandId = brandId();
        accountMapper.ensureAccount(brandId, memberId);
        MktPointsAccount acc = accountMapper.selectByMember(brandId, memberId);
        PointsAccountVO vo = new PointsAccountVO();
        if (acc == null) {
            vo.setMemberId(memberId);
            vo.setTotalPoints(0L);
            vo.setTotalEarned(0L);
            vo.setTotalConsumed(0L);
            vo.setTotalReversed(0L);
            return vo;
        }
        vo.setMemberId(acc.getMemberId());
        vo.setTotalPoints(acc.getTotalPoints());
        vo.setTotalEarned(acc.getTotalEarned());
        vo.setTotalConsumed(acc.getTotalConsumed());
        vo.setTotalReversed(acc.getTotalReversed());
        vo.setUpdateTime(acc.getUpdateTime());
        return vo;
    }

    // ==================== 消费得积分 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean earnByOrder(Long brandId, Long memberId, Long storeId, String orderNo,
                               Long paidAmount, String traceId) {
        if (memberId == null || orderNo == null || paidAmount == null || paidAmount <= 0) {
            log.debug("[POINTS] 缺少会员或金额，跳过发积分 orderNo={}", orderNo);
            return false;
        }
        return earnInternal(brandId, memberId, storeId, orderNo, paidAmount, 100, traceId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean earnByOrderWithLevel(Long brandId, Long memberId, Long storeId, String orderNo,
                                        Long paidAmount, Long levelId, String traceId) {
        int rate = 100;
        if (levelId != null) {
            rate = levelBenefitService.pointRateOf(brandId, levelId);
        }
        return earnInternal(brandId, memberId, storeId, orderNo, paidAmount, rate, traceId);
    }

    /** 积分计算与发放的公共实现（rate 为倍率 ×100）。 */
    private boolean earnInternal(Long brandId, Long memberId, Long storeId, String orderNo,
                                 Long paidAmount, int rate, String traceId) {
        if (memberId == null || orderNo == null || paidAmount == null || paidAmount <= 0) {
            log.debug("[POINTS] 缺少会员或金额，跳过发积分 orderNo={}", orderNo);
            return false;
        }
        MktPointsRule rule = ruleMapper.selectByCode(brandId, PointsBizType.ORDER_EARN.name());
        long per = (rule == null || rule.getAmountPerPoint() == null || rule.getAmountPerPoint() <= 0)
                ? 100L : rule.getAmountPerPoint();
        // 基础分 = 金额/单位；等级倍率在基础分上乘（乘后再取整，避免每单位截断累积误差）
        long basePoints = paidAmount / per;
        long points = basePoints * rate / 100;
        if (points <= 0) {
            log.debug("[POINTS] 消费 {} 分不足 {} 分，不计积分 orderNo={}", paidAmount, per, orderNo);
            return false;
        }
        int expireDays = (rule == null || rule.getExpireDays() == null) ? (int) DEFAULT_EXPIRE_DAYS : rule.getExpireDays();

        return doEarn(brandId, memberId, storeId, PointsBizType.ORDER_EARN, orderNo,
                points, expireDays, "订单 " + orderNo + " 消费得积分", traceId);
    }

    /**
     * 发放积分的统一入口。
     *
     * <p>唯一索引冲突即视为"已发过"，返回 false，不抛异常
     * （让调用方把重复消息当作正常幂等处理）。
     */
    private boolean doEarn(Long brandId, Long memberId, Long storeId, PointsBizType bizType,
                           String bizNo, long points, int expireDays, String remark, String traceId) {
        accountMapper.ensureAccount(brandId, memberId);
        MktPointsAccount acc = accountMapper.selectByMember(brandId, memberId);
        if (acc == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "积分账户初始化失败");
        }
        long before = acc.getTotalPoints() == null ? 0L : acc.getTotalPoints();

        MktPointsTransaction txn = new MktPointsTransaction();
        txn.setBrandId(brandId);
        txn.setAccountId(acc.getId());
        txn.setMemberId(memberId);
        txn.setTxnNo(BizNoGenerator.of("PT"));
        txn.setBizType(bizType.name());
        txn.setBizNo(bizNo);
        txn.setChangePoints(points);
        txn.setBeforePoints(before);
        txn.setAfterPoints(before + points);
        txn.setStoreId(storeId);
        txn.setRemark(remark);
        txn.setTraceId(traceId);
        // 先查后插：避免靠捕获 DuplicateKeyException 做幂等。
        //
        // 为什么不能靠 catch：MyBatis 的 insert 抛异常后，Spring 事务已被标记
        // rollback-only；即使这里 return false（看似正常返回），
        // 外层提交时仍会抛 UnexpectedRollbackException，
        // 消息被 nack 重投 —— 与「幂等」的意图正好相反。
        if (txnMapper.selectByBiz(brandId, bizType.name(), bizNo) != null) {
            log.info("[POINTS] 该业务单已发放积分，忽略重复 bizType={} bizNo={}", bizType, bizNo);
            return false;
        }
        try {
            txnMapper.insert(txn);
        } catch (DuplicateKeyException e) {
            // 并发下仍可能撞唯一键：此时事务已不可用，标记 rollback 并抛出，
            // 由消费者按"系统异常"重试；重试时上面的 selectByBiz 会命中并正常返回。
            log.info("[POINTS] 并发重复发放，交给重试 bizType={} bizNo={}", bizType, bizNo);
            org.springframework.transaction.interceptor.TransactionAspectSupport
                    .currentTransactionStatus().setRollbackOnly();
            throw e;
        }

        int rows = accountMapper.addPoints(brandId, memberId, points);
        if (rows == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "积分入账失败");
        }

        // 落批次（供过期处理）
        if (expireDays > 0) {
            MktPointsBatch batch = new MktPointsBatch();
            batch.setBrandId(brandId);
            batch.setMemberId(memberId);
            batch.setSourceTxnId(txn.getId());
            batch.setPoints(points);
            batch.setRemainPoints(points);
            batch.setExpireTime(LocalDateTime.now().plusDays(expireDays));
            batch.setStatus("ACTIVE");
            batchMapper.insert(batch);
        }

        log.info("[POINTS] 发放成功 memberId={} +{} 类型={} 单号={}", memberId, points, bizType, bizNo);
        syncSnapshot(memberId, before + points);
        return true;
    }

    // ==================== 退款冲正 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reverseByRefund(Long brandId, Long memberId, String orderNo,
                                   Long refundAmount, String traceId) {
        if (orderNo == null) {
            return false;
        }
        // 按原发放流水冲正，不重新计算
        MktPointsTransaction origin = txnMapper.selectByBiz(brandId,
                PointsBizType.ORDER_EARN.name(), orderNo);
        if (origin == null) {
            log.info("[POINTS] 订单无积分发放记录，无需冲正 orderNo={}", orderNo);
            return false;
        }
        long originalPoints = origin.getChangePoints() == null ? 0L : origin.getChangePoints();
        if (originalPoints <= 0) {
            return false;
        }

        // REFUND_SUCCEEDED 事件不带 memberId：以原发放流水里的会员为准
        Long actualMemberId = memberId != null ? memberId : origin.getMemberId();
        if (actualMemberId == null) {
            log.warn("[POINTS] 无法确定退款积分归属会员，跳过 orderNo={}", orderNo);
            return false;
        }

        // 已冲正过则直接返回（唯一索引也会拦，这里提前判更好读）
        MktPointsTransaction reversed = txnMapper.selectByBiz(brandId,
                PointsBizType.ORDER_REFUND_REVERSE.name(), orderNo);
        if (reversed != null) {
            log.info("[POINTS] 该订单已冲正过，忽略 orderNo={}", orderNo);
            return false;
        }

        MktPointsAccount acc = accountMapper.selectByMember(brandId, actualMemberId);
        if (acc == null) {
            return false;
        }
        long before = acc.getTotalPoints() == null ? 0L : acc.getTotalPoints();
        // 余额不足时按现有余额冲正（不能把积分冲成负数）
        long reversePoints = Math.min(originalPoints, before);
        if (reversePoints <= 0) {
            log.info("[POINTS] 当前积分为 0，无可冲正 orderNo={}", orderNo);
            return false;
        }

        MktPointsTransaction txn = new MktPointsTransaction();
        txn.setBrandId(brandId);
        txn.setAccountId(acc.getId());
        txn.setMemberId(actualMemberId);
        txn.setTxnNo(BizNoGenerator.of("PT"));
        txn.setBizType(PointsBizType.ORDER_REFUND_REVERSE.name());
        txn.setBizNo(orderNo);
        txn.setChangePoints(-reversePoints);
        txn.setBeforePoints(before);
        txn.setAfterPoints(before - reversePoints);
        txn.setRemark("订单 " + orderNo + " 退款冲正" + (refundAmount != null ? "，退款额 " + refundAmount + " 分" : ""));
        txn.setTraceId(traceId);
        try {
            txnMapper.insert(txn);
        } catch (DuplicateKeyException e) {
            log.info("[POINTS] 冲正流水已存在，忽略 orderNo={}", orderNo);
            return false;
        }

        int rows = accountMapper.reversePoints(brandId, actualMemberId, reversePoints);
        if (rows == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "积分冲正失败（余额不足）");
        }

        // 消耗批次（先进先出）
        consumeBatches(brandId, actualMemberId, reversePoints);

        log.info("[POINTS] 冲正成功 memberId={} -{} orderNo={}", actualMemberId, reversePoints, orderNo);
        syncSnapshot(actualMemberId, before - reversePoints);
        return true;
    }

    /** 按到期时间先进先出消耗批次。 */
    private void consumeBatches(Long brandId, Long memberId, long points) {
        long remain = points;
        List<MktPointsBatch> batches = batchMapper.selectConsumable(brandId, memberId);
        for (MktPointsBatch b : batches) {
            if (remain <= 0) {
                break;
            }
            long take = Math.min(remain, b.getRemainPoints());
            int rows = batchMapper.consume(b.getId(), take);
            if (rows > 0) {
                remain -= take;
            }
        }
        if (remain > 0) {
            log.warn("[POINTS] 批次可消耗量不足，剩余 {} 未从批次扣除（账户余额已正确扣减）", remain);
        }
    }

    // ==================== 签到 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SignInResultVO signIn(Long memberId) {
        Long brandId = brandId();
        LocalDate today = LocalDate.now();

        MktSignIn exist = signInMapper.selectByDate(brandId, memberId, today);
        if (exist != null) {
            // 已签到：幂等返回，不重复发积分
            PointsAccountVO acc = account(memberId);
            SignInResultVO vo = new SignInResultVO();
            vo.setPoints(0L);
            vo.setContinuousDays(exist.getContinuousDays());
            vo.setSignDate(today);
            vo.setFirstTime(false);
            vo.setTotalPoints(acc.getTotalPoints());
            vo.setBonusRemark("今日已签到");
            return vo;
        }

        MktPointsRule rule = ruleMapper.selectByCode(brandId, PointsBizType.SIGN_IN.name());
        long base = (rule == null || rule.getSignInPoints() == null) ? 0L : rule.getSignInPoints();
        int maxDays = (rule == null || rule.getSignInContinuousMax() == null) ? 7 : rule.getSignInContinuousMax();
        long bonus = (rule == null || rule.getSignInBonus() == null) ? 0L : rule.getSignInBonus();

        // 连续天数：昨天签过则 +1，否则重置为 1
        MktSignIn latest = signInMapper.selectLatest(brandId, memberId);
        int continuous = 1;
        if (latest != null && latest.getSignDate() != null
                && latest.getSignDate().plusDays(1).equals(today)) {
            continuous = (latest.getContinuousDays() == null ? 1 : latest.getContinuousDays()) + 1;
        }
        int bonusDays = Math.min(continuous, maxDays);
        long total = base + bonus * (bonusDays - 1);
        if (total < 0) {
            total = 0;
        }

        MktSignIn rec = new MktSignIn();
        rec.setBrandId(brandId);
        rec.setMemberId(memberId);
        rec.setSignDate(today);
        rec.setContinuousDays(continuous);
        rec.setPoints(total);
        try {
            signInMapper.insert(rec);
        } catch (DuplicateKeyException e) {
            // 并发重复签到：唯一键兜住
            log.info("[POINTS] 并发重复签到已忽略 memberId={} date={}", memberId, today);
            PointsAccountVO acc = account(memberId);
            SignInResultVO vo = new SignInResultVO();
            vo.setPoints(0L);
            vo.setContinuousDays(continuous);
            vo.setSignDate(today);
            vo.setFirstTime(false);
            vo.setTotalPoints(acc.getTotalPoints());
            return vo;
        }

        if (total > 0) {
            // bizNo 必须带会员 ID。
            // 唯一索引是 (brand_id, biz_type, biz_no)，
            // 若只用日期，同一天第二个签到的人会撞上第一个人的流水，
            // 表现为「签到记录写了但积分没加」——已实测复现。
            doEarn(brandId, memberId, null, PointsBizType.SIGN_IN,
                    memberId + "-" + today.format(DATE_FMT), total,
                    rule == null || rule.getExpireDays() == null ? (int) DEFAULT_EXPIRE_DAYS : rule.getExpireDays(),
                    "签到第 " + continuous + " 天", null);
        }

        // 注意：不能用 account(memberId) —— 它带 @Transactional，
        // 在同一事务内调用会命中 MyBatis 一级缓存，读到积分入账【之前】的旧值，
        // 表现为"签到成功但返回的总积分是 0"。这里直接从 doEarn 的结果推算。
        long newTotal;
        MktPointsAccount fresh = accountMapper.selectByMember(brandId, memberId);
        newTotal = fresh == null || fresh.getTotalPoints() == null ? 0L : fresh.getTotalPoints();
        SignInResultVO vo = new SignInResultVO();
        vo.setPoints(total);
        vo.setContinuousDays(continuous);
        vo.setSignDate(today);
        vo.setFirstTime(true);
        vo.setTotalPoints(newTotal);
        vo.setBonusRemark(bonusDays > 1 ? "连续 " + continuous + " 天，额外加成 " + (bonusDays - 1) + " 天" : null);

        // 连续签到活动：达到活动配置的 continuousDays 即发奖（唯一键保证同一业务只发一次）
        try {
            int granted = activityService.triggerContinuousSign(brandId, memberId, continuous);
            if (granted > 0) {
                log.info("[SIGN-IN] 触发连续签到活动发奖 memberId={} continuous={} count={}",
                        memberId, continuous, granted);
            }
        } catch (Exception e) {
            log.warn("[SIGN-IN] 触发连续签到活动失败 memberId={} err={}", memberId, e.getMessage());
        }
        return vo;
    }

    // ==================== 查询 ====================

    @Override
    public List<PointsTransactionVO> transactions(Long memberId, String bizType, int limit) {
        return txnMapper.selectByMember(memberId, bizType, limit <= 0 ? 50 : Math.min(limit, 200))
                .stream().map(t -> {
                    PointsTransactionVO vo = new PointsTransactionVO();
                    vo.setId(t.getId());
                    vo.setTxnNo(t.getTxnNo());
                    vo.setBizType(t.getBizType());
                    try {
                        vo.setBizTypeLabel(PointsBizType.valueOf(t.getBizType()).getLabel());
                    } catch (Exception e) {
                        vo.setBizTypeLabel(t.getBizType());
                    }
                    vo.setBizNo(t.getBizNo());
                    vo.setChangePoints(t.getChangePoints());
                    vo.setBeforePoints(t.getBeforePoints());
                    vo.setAfterPoints(t.getAfterPoints());
                    vo.setRemark(t.getRemark());
                    vo.setCreateTime(t.getCreateTime());
                    return vo;
                }).toList();
    }

    @Override
    public List<PointsRuleVO> listRules() {
        Long brandId = brandId();
        return ruleMapper.selectByBrand(brandId).stream().map(r -> {
            PointsRuleVO vo = new PointsRuleVO();
            vo.setId(r.getId());
            vo.setRuleCode(r.getRuleCode());
            vo.setRuleName(r.getRuleName());
            vo.setAmountPerPoint(r.getAmountPerPoint());
            vo.setSignInPoints(r.getSignInPoints());
            vo.setSignInContinuousMax(r.getSignInContinuousMax());
            vo.setSignInBonus(r.getSignInBonus());
            vo.setExpireDays(r.getExpireDays());
            vo.setStatus(r.getStatus());
            vo.setUsedBy(switch (r.getRuleCode() == null ? "" : r.getRuleCode()) {
                case "ORDER_EARN" -> "订单支付成功后，按实付金额换算积分";
                case "SIGN_IN" -> "每日签到，连续签到按天数加成";
                default -> r.getRuleName();
            });
            return vo;
        }).toList();
    }

    // ==================== 人工调整 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean adjust(Long memberId, long points, String bizNo, String remark) {
        if (points == 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "调整积分不能为 0");
        }
        Long brandId = brandId();
        String key = bizNo == null || bizNo.isBlank() ? BizNoGenerator.of("ADJ") : bizNo;
        if (points > 0) {
            return doEarn(brandId, memberId, null, PointsBizType.ADJUST, key, points,
                    (int) DEFAULT_EXPIRE_DAYS, remark == null ? "人工调整" : remark, null);
        }
        long deduct = -points;
        MktPointsAccount acc = accountMapper.selectByMember(brandId, memberId);
        if (acc == null) {
            throw new BizException(ResultCode.NOT_FOUND, "积分账户不存在");
        }
        long before = acc.getTotalPoints() == null ? 0L : acc.getTotalPoints();

        MktPointsTransaction txn = new MktPointsTransaction();
        txn.setBrandId(brandId);
        txn.setAccountId(acc.getId());
        txn.setMemberId(memberId);
        txn.setTxnNo(BizNoGenerator.of("PT"));
        txn.setBizType(PointsBizType.DEDUCT.name());
        txn.setBizNo(key);
        txn.setChangePoints(-deduct);
        txn.setBeforePoints(before);
        txn.setAfterPoints(before - deduct);
        txn.setRemark(remark == null ? "人工扣减" : remark);
        try {
            txnMapper.insert(txn);
        } catch (DuplicateKeyException e) {
            return false;
        }
        int rows = accountMapper.deductPoints(brandId, memberId, deduct);
        if (rows == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "积分不足，扣减失败");
        }
        accountMapper.addConsumed(brandId, memberId, deduct);
        consumeBatches(brandId, memberId, deduct);
        syncSnapshot(memberId, before - deduct);
        return true;
    }

    // ==================== 过期 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int expireBatches(int limit) {
        Long brandId = brandId();
        List<MktPointsBatch> expired = batchMapper.selectExpired(LocalDateTime.now(),
                limit <= 0 ? 200 : limit);
        int n = 0;
        for (MktPointsBatch b : expired) {
            long pts = b.getRemainPoints() == null ? 0L : b.getRemainPoints();
            if (pts <= 0) {
                batchMapper.markExpired(b.getId());
                continue;
            }
            MktPointsAccount acc = accountMapper.selectByMember(brandId, b.getMemberId());
            if (acc == null) {
                batchMapper.markExpired(b.getId());
                continue;
            }
            long before = acc.getTotalPoints() == null ? 0L : acc.getTotalPoints();
            long cut = Math.min(pts, before);

            MktPointsTransaction txn = new MktPointsTransaction();
            txn.setBrandId(brandId);
            txn.setAccountId(acc.getId());
            txn.setMemberId(b.getMemberId());
            txn.setTxnNo(BizNoGenerator.of("PT"));
            txn.setBizType(PointsBizType.EXPIRE.name());
            txn.setBizNo("BATCH-" + b.getId());
            txn.setChangePoints(-cut);
            txn.setBeforePoints(before);
            txn.setAfterPoints(before - cut);
            txn.setRemark("积分批次过期");
            try {
                txnMapper.insert(txn);
            } catch (DuplicateKeyException e) {
                batchMapper.markExpired(b.getId());
                continue;
            }
            if (cut > 0) {
                accountMapper.deductPoints(brandId, b.getMemberId(), cut);
                accountMapper.addConsumed(brandId, b.getMemberId(), cut);
                syncSnapshot(b.getMemberId(), before - cut);
            }
            batchMapper.markExpired(b.getId());
            n++;
        }
        if (n > 0) {
            log.info("[POINTS] 过期处理完成，处理批次数={}", n);
        }
        return n;
    }

    // ==================== 快照回写 ====================

    /**
     * 回写 member_db 的积分快照。
     *
     * <p><b>覆盖写而非增减</b>：账本在 marketing_db，这里只是把最新总额同步过去，
     * 避免"两边各加一次"导致对不上。
     *
     * <p><b>失败只告警不抛异常</b>：会员服务不可用不应该让积分发放回滚 ——
     * 积分已经在本库正确入账，快照落后可以后续补数纠正。
     */
    @Override
    public void syncSnapshot(Long memberId, long points) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("points", points);
            memberSnapshotClient.syncPoints(memberId, body);
        } catch (Exception e) {
            log.warn("[POINTS] 积分快照回写失败（不影响账本）memberId={} points={} err={}",
                    memberId, points, e.getMessage());
        }
    }

    private Long brandId() {
        Long b = com.teaclub.cloud.security.context.UserContext.currentBrandIdOrNull();
        return b == null ? 1L : b;
    }
}
