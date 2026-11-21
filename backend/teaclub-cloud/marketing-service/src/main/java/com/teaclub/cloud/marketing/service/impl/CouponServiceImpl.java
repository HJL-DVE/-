package com.teaclub.cloud.marketing.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.core.util.BizNoGenerator;
import com.teaclub.cloud.marketing.entity.MktCoupon;
import com.teaclub.cloud.marketing.entity.MktCouponLog;
import com.teaclub.cloud.marketing.entity.MktCouponReceiveRecord;
import com.teaclub.cloud.marketing.entity.MktCouponTemplate;
import com.teaclub.cloud.marketing.enums.CouponLogAction;
import com.teaclub.cloud.marketing.enums.CouponScopeType;
import com.teaclub.cloud.marketing.enums.CouponStatus;
import com.teaclub.cloud.marketing.enums.CouponType;
import com.teaclub.cloud.marketing.mapper.MktCouponLogMapper;
import com.teaclub.cloud.marketing.mapper.MktCouponMapper;
import com.teaclub.cloud.marketing.mapper.MktCouponReceiveRecordMapper;
import com.teaclub.cloud.marketing.mapper.MktCouponTemplateMapper;
import com.teaclub.cloud.marketing.service.CouponService;
import com.teaclub.cloud.marketing.vo.CouponQuoteVO;
import com.teaclub.cloud.marketing.vo.CouponVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 优惠券服务实现。
 *
 * <p><b>为什么全部用条件 UPDATE</b>：
 * 「先 SELECT 判断状态，再 UPDATE」在并发下会出现两个线程都读到 UNUSED、
 * 都判定可核销、都执行 UPDATE 的双重核销。
 * 改成 {@code UPDATE ... WHERE status='UNUSED'} 后，数据库行锁保证只有一条成功，
 * 另一条受影响行数为 0，天然幂等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private static final String REDIS_LOCK_PREFIX = "mkt:coupon:lock:";
    private static final String REDIS_RECEIVE_PREFIX = "mkt:coupon:receive:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);
    /**
     * 领取并发窗口。
     *
     * <p>只用于拦截「同一会员在同一瞬间连点多次」造成的瞬时重复请求，
     * 因此必须极短（毫秒级）。这个值曾经是 5 秒，
     * 结果把「限领 2 张、连续领两次」这种<b>正常业务</b>误判成重复请求。
     * 真正的限领约束由数据库计数负责，Redis 只是削峰。
     */
    private static final Duration RECEIVE_TTL = Duration.ofMillis(800);

    private final MktCouponTemplateMapper templateMapper;
    private final MktCouponMapper couponMapper;
    private final MktCouponLogMapper logMapper;
    private final MktCouponReceiveRecordMapper recordMapper;
    private final StringRedisTemplate redis;

    // ==================== 领取 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<CouponVO> receive(Long templateId, Long memberId, int qty) {
        if (qty <= 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "领取数量必须大于 0");
        }
        MktCouponTemplate tpl = templateMapper.selectById(templateId);
        if (tpl == null || tpl.getDeleted() == 1) {
            throw new BizException(ResultCode.NOT_FOUND, "优惠券模板不存在");
        }
        if (!"ENABLED".equals(tpl.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "该优惠券当前不可领取");
        }

        // ===== 第一层：Redis 快速拦截同一会员的高频重复领取 =====
        String receiveKey = REDIS_RECEIVE_PREFIX + templateId + ":" + memberId;
        Boolean first = redis.opsForValue().setIfAbsent(receiveKey, "1", RECEIVE_TTL);
        if (Boolean.FALSE.equals(first)) {
            throw new BizException(ResultCode.IDEMPOTENT_CONFLICT, "操作过于频繁，请稍后再试");
        }

        // ===== 第二层：限领校验（DB 为准）=====
        int received = couponMapper.countByTemplateAndMember(templateId, memberId);
        int limit = tpl.getPerMemberLimit() == null ? 1 : tpl.getPerMemberLimit();
        if (received + qty > limit) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "每人限领 " + limit + " 张，您已领取 " + received + " 张");
        }

        List<CouponVO> result = new ArrayList<>(qty);
        for (int i = 0; i < qty; i++) {
            // ===== 第三层：发行量原子递增，发完即止（并发安全）=====
            int issued = templateMapper.tryIssueOne(templateId);
            if (issued == 0) {
                throw new BizException(ResultCode.BAD_REQUEST, "该优惠券已领完");
            }

            MktCoupon coupon = buildCoupon(tpl, memberId, "RECEIVE");
            couponMapper.insert(coupon);

            MktCouponReceiveRecord rec = new MktCouponReceiveRecord();
            rec.setBrandId(tpl.getBrandId());
            rec.setTemplateId(templateId);
            rec.setMemberId(memberId);
            rec.setCouponId(coupon.getId());
            rec.setCouponNo(coupon.getCouponNo());
            rec.setQty(1);
            recordMapper.insert(rec);

            result.add(toVO(coupon, tpl.getCouponName()));
        }
        log.info("[COUPON] 领取成功 templateId={} memberId={} count={}", templateId, memberId, qty);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponVO grant(Long templateId, Long memberId, String obtainType) {
        MktCouponTemplate tpl = templateMapper.selectById(templateId);
        if (tpl == null || tpl.getDeleted() == 1) {
            throw new BizException(ResultCode.NOT_FOUND, "优惠券模板不存在: " + templateId);
        }
        if (!"ENABLED".equals(tpl.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "券模板未启用，无法发放: " + tpl.getCouponName());
        }
        // 只受发行总量约束，不受每人限领约束
        if (templateMapper.tryIssueOne(templateId) == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "该券已发完: " + tpl.getCouponName());
        }
        MktCoupon coupon = buildCoupon(tpl, memberId,
                obtainType == null || obtainType.isBlank() ? "GRANT" : obtainType);
        couponMapper.insert(coupon);
        log.info("[COUPON] 发放成功 templateId={} memberId={} couponNo={} 来源={}",
                templateId, memberId, coupon.getCouponNo(), coupon.getObtainType());
        return toVO(coupon, tpl.getCouponName());
    }

    @Override
    public List<com.teaclub.cloud.marketing.vo.CouponTemplateVO> listAvailableTemplates(
            Long memberId, Boolean birthdayOnly, Boolean newMemberOnly) {
        var w = com.baomidou.mybatisplus.core.toolkit.Wrappers.<MktCouponTemplate>lambdaQuery()
                .eq(MktCouponTemplate::getDeleted, 0)
                .eq(MktCouponTemplate::getStatus, "ENABLED")
                .orderByAsc(MktCouponTemplate::getId);
        if (birthdayOnly != null) {
            w.eq(MktCouponTemplate::getBirthdayOnly, birthdayOnly ? 1 : 0);
        }
        if (newMemberOnly != null) {
            w.eq(MktCouponTemplate::getNewMemberOnly, newMemberOnly ? 1 : 0);
        }
        List<com.teaclub.cloud.marketing.vo.CouponTemplateVO> out = new ArrayList<>();
        for (MktCouponTemplate t : templateMapper.selectList(w)) {
            // 发完的不再展示
            if (t.getTotalCount() != null && t.getTotalCount() > 0
                    && t.getIssuedCount() != null && t.getIssuedCount() >= t.getTotalCount()) {
                continue;
            }
            com.teaclub.cloud.marketing.vo.CouponTemplateVO vo =
                    new com.teaclub.cloud.marketing.vo.CouponTemplateVO();
            vo.setId(t.getId());
            vo.setTemplateNo(t.getTemplateNo());
            vo.setCouponName(t.getCouponName());
            vo.setCouponType(t.getCouponType());
            try {
                vo.setCouponTypeLabel(CouponType.valueOf(t.getCouponType()).getLabel());
            } catch (Exception e) {
                vo.setCouponTypeLabel(t.getCouponType());
            }
            vo.setScopeType(t.getScopeType());
            vo.setFaceValue(t.getFaceValue());
            vo.setDiscountRate(t.getDiscountRate());
            vo.setMaxDiscountAmount(t.getMaxDiscountAmount());
            vo.setThresholdAmount(t.getThresholdAmount());
            vo.setTotalCount(t.getTotalCount());
            vo.setIssuedCount(t.getIssuedCount());
            vo.setPerMemberLimit(t.getPerMemberLimit());
            vo.setValidType(t.getValidType());
            vo.setValidDays(t.getValidDays());
            vo.setNewMemberOnly(t.getNewMemberOnly());
            vo.setBirthdayOnly(t.getBirthdayOnly());
            vo.setStatus(t.getStatus());
            vo.setRemark(t.getRemark());
            if (memberId != null) {
                vo.setReceivedCount(couponMapper.countByTemplateAndMember(t.getId(), memberId));
            }
            out.add(vo);
        }
        return out;
    }

    /** 按模板生成一张券实例，有效期按 validType 计算。 */
    private MktCoupon buildCoupon(MktCouponTemplate tpl, Long memberId, String obtainType) {
        MktCoupon c = new MktCoupon();
        c.setBrandId(tpl.getBrandId());
        c.setCouponNo(BizNoGenerator.of("CP"));
        c.setTemplateId(tpl.getId());
        c.setCouponType(tpl.getCouponType());
        c.setMemberId(memberId);
        c.setFaceValue(tpl.getFaceValue());
        c.setDiscountRate(tpl.getDiscountRate());
        c.setMaxDiscountAmount(tpl.getMaxDiscountAmount());
        c.setThresholdAmount(tpl.getThresholdAmount());
        c.setScopeType(tpl.getScopeType());
        c.setExchangeSpuId(tpl.getExchangeSpuId());
        c.setExchangeSkuId(tpl.getExchangeSkuId());
        c.setStatus(CouponStatus.UNUSED.name());
        c.setObtainType(obtainType);
        c.setObtainTime(LocalDateTime.now());
        c.setVersion(0);

        LocalDateTime now = LocalDateTime.now();
        if ("RELATIVE".equals(tpl.getValidType())) {
            int days = tpl.getValidDays() == null ? 30 : tpl.getValidDays();
            c.setValidStart(now);
            c.setValidEnd(now.plusDays(days));
        } else {
            c.setValidStart(tpl.getValidStart() == null ? now : tpl.getValidStart());
            c.setValidEnd(tpl.getValidEnd() == null ? now.plusYears(1) : tpl.getValidEnd());
        }
        return c;
    }

    // ==================== 查询 ====================

    @Override
    public List<CouponVO> listMine(Long memberId, String status, int limit) {
        return couponMapper.selectByMember(memberId, status, limit <= 0 ? 50 : Math.min(limit, 200))
                .stream().map(c -> toVO(c, null)).toList();
    }

    @Override
    public List<CouponVO> listUsable(Long memberId, Long orderAmount, Long storeId) {
        return couponMapper.selectUsable(memberId).stream().map(c -> {
            CouponVO vo = toVO(c, null);
            // 传了订单金额才做门槛判定；不满足则标 unusable，前端置灰
            if (orderAmount != null && orderAmount > 0) {
                long threshold = c.getThresholdAmount() == null ? 0L : c.getThresholdAmount();
                if (orderAmount < threshold) {
                    vo.setUsable(false);
                    vo.setUnusableReason("满" + (threshold / 100) + "元可用");
                }
            }
            return vo;
        }).toList();
    }

    // ==================== 试算 ====================

    @Override
    public CouponQuoteVO quoteById(Long couponId, Long memberId, Long storeId,
                                   long orderAmount, List<Long> spuIds) {
        MktCoupon coupon = couponId == null ? null : couponMapper.selectById(couponId);
        if (coupon == null) {
            CouponQuoteVO vo = new CouponQuoteVO();
            vo.setAvailable(false);
            vo.setReasonCode("COUPON_NOT_FOUND");
            vo.setReason("优惠券不存在");
            vo.setDiscountAmount(0L);
            return vo;
        }
        return quote(coupon.getCouponNo(), memberId, storeId, orderAmount, spuIds);
    }

    @Override
    public CouponQuoteVO quote(String couponNo, Long memberId, Long storeId,
                               long orderAmount, List<Long> spuIds) {
        CouponQuoteVO vo = new CouponQuoteVO();
        vo.setCouponNo(couponNo);
        vo.setAvailable(false);

        MktCoupon c;
        if (couponNo != null && !couponNo.isBlank()) {
            c = couponMapper.selectByNo(couponNo);
        } else {
            vo.setReasonCode("NO_COUPON");
            vo.setReason("未选择优惠券");
            vo.setDiscountAmount(0L);
            return vo;
        }
        if (c == null) {
            vo.setReasonCode("NOT_FOUND");
            vo.setReason("优惠券不存在");
            vo.setDiscountAmount(0L);
            return vo;
        }
        vo.setCouponId(c.getId());
        vo.setCouponType(c.getCouponType());

        if (!c.getMemberId().equals(memberId)) {
            vo.setReasonCode("NOT_OWNER");
            vo.setReason("该券不属于当前会员");
            vo.setDiscountAmount(0L);
            return vo;
        }
        if (!CouponStatus.UNUSED.name().equals(c.getStatus())) {
            vo.setReasonCode("STATUS_" + c.getStatus());
            vo.setReason("券状态为 " + c.getStatus() + "，不可使用");
            vo.setDiscountAmount(0L);
            return vo;
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(c.getValidStart()) || now.isAfter(c.getValidEnd())) {
            vo.setReasonCode("EXPIRED");
            vo.setReason("优惠券不在有效期内");
            vo.setDiscountAmount(0L);
            return vo;
        }

        // 门店范围校验
        if (CouponScopeType.STORE.name().equals(c.getScopeType()) && storeId != null) {
            Boolean inScope = templateScopeContainsStore(c.getTemplateId(), storeId);
            if (Boolean.FALSE.equals(inScope)) {
                vo.setReasonCode("STORE_NOT_ALLOWED");
                vo.setReason("该券不可在当前门店使用");
                vo.setDiscountAmount(0L);
                return vo;
            }
        }
        // 商品范围校验
        if (CouponScopeType.GOODS.name().equals(c.getScopeType()) && spuIds != null && !spuIds.isEmpty()) {
            Boolean hasMatch = templateScopeHitsSpu(c.getTemplateId(), spuIds);
            if (Boolean.FALSE.equals(hasMatch)) {
                vo.setReasonCode("GOODS_NOT_ALLOWED");
                vo.setReason("订单中没有该券适用的商品");
                vo.setDiscountAmount(0L);
                return vo;
            }
        }

        // 门槛校验
        long threshold = c.getThresholdAmount() == null ? 0L : c.getThresholdAmount();
        if (threshold > 0 && orderAmount < threshold) {
            vo.setReasonCode("THRESHOLD_NOT_MET");
            vo.setReason("订单金额未满 " + (threshold / 100) + " 元");
            vo.setDiscountAmount(0L);
            return vo;
        }

        long discount = calculateDiscount(c, orderAmount);
        if (discount <= 0) {
            vo.setReasonCode("ZERO_DISCOUNT");
            vo.setReason("该券在本单无实际抵扣");
            vo.setDiscountAmount(0L);
            return vo;
        }

        vo.setAvailable(true);
        vo.setDiscountAmount(discount);
        return vo;
    }

    /**
     * 计算抵扣金额。
     *
     * <p>折扣券按比例算并受封顶限制；其余按面额，且不超过订单金额（不能倒找钱）。
     */
    private long calculateDiscount(MktCoupon c, long orderAmount) {
        CouponType type = CouponType.valueOf(c.getCouponType());
        if (type.isExchange()) {
            // 兑换券不抵扣金额，由 order-service 单独处理（本期返回 0）
            return 0L;
        }
        if (type.isDiscount()) {
            int rate = c.getDiscountRate() == null ? 10000 : c.getDiscountRate();
            long off = Math.round(orderAmount * (10000L - rate) / 10000.0);
            Long cap = c.getMaxDiscountAmount();
            if (cap != null && cap > 0 && off > cap) {
                off = cap;
            }
            return Math.min(off, orderAmount);
        }
        long face = c.getFaceValue() == null ? 0L : c.getFaceValue();
        return Math.min(face, orderAmount);
    }

    private Boolean templateScopeContainsStore(Long templateId, Long storeId) {
        return templateMapper.countStoreScope(templateId, storeId) > 0;
    }

    private Boolean templateScopeHitsSpu(Long templateId, List<Long> spuIds) {
        return templateMapper.countSpuScope(templateId, spuIds) > 0;
    }

    // ==================== 锁定 / 核销 / 释放 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean lock(String couponNo, Long memberId, String orderNo) {
        MktCoupon c = couponMapper.selectByNo(couponNo);
        if (c == null) {
            throw new BizException(ResultCode.NOT_FOUND, "优惠券不存在");
        }
        // 已经是本单锁定的，直接幂等成功
        if (CouponStatus.LOCKED.name().equals(c.getStatus())
                && orderNo.equals(c.getLockedOrderNo())) {
            return true;
        }
        if (!CouponStatus.UNUSED.name().equals(c.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "券状态为 " + c.getStatus() + "，不可锁定");
        }

        String lockKey = REDIS_LOCK_PREFIX + couponNo + ":" + orderNo;
        Boolean ok = redis.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (Boolean.FALSE.equals(ok)) {
            throw new BizException(ResultCode.IDEMPOTENT_CONFLICT, "该券正在被处理，请稍后重试");
        }

        int rows = couponMapper.tryLock(c.getId(), memberId, orderNo);
        if (rows == 0) {
            redis.delete(lockKey);
            // 并发下被别人抢先：再查一次，若已是本单则视为成功
            MktCoupon again = couponMapper.selectByNo(couponNo);
            if (again != null && CouponStatus.LOCKED.name().equals(again.getStatus())
                    && orderNo.equals(again.getLockedOrderNo())) {
                return true;
            }
            throw new BizException(ResultCode.BAD_REQUEST, "优惠券锁定失败（可能已过期或被占用）");
        }
        writeLog(c, CouponLogAction.LOCK, orderNo, null, CouponStatus.UNUSED.name(),
                CouponStatus.LOCKED.name());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean lockById(Long couponId, Long memberId, String orderNo) {
        MktCoupon coupon = couponId == null ? null : couponMapper.selectById(couponId);
        if (coupon == null) {
            log.warn("[COUPON] 锁定时券不存在 couponId={} orderNo={}", couponId, orderNo);
            return false;
        }
        return lock(coupon.getCouponNo(), memberId, orderNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean useById(Long couponId, String orderNo, Long discountAmount) {
        MktCoupon coupon = couponId == null ? null : couponMapper.selectById(couponId);
        if (coupon == null) {
            log.warn("[COUPON] 核销时券不存在 couponId={} orderNo={}", couponId, orderNo);
            return false;
        }
        return use(coupon.getCouponNo(), orderNo, discountAmount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean use(String couponNo, String orderNo, Long discountAmount) {
        MktCoupon c = couponMapper.selectByNo(couponNo);
        if (c == null) {
            throw new BizException(ResultCode.NOT_FOUND, "优惠券不存在");
        }
        // 已核销且是同一订单 → 幂等成功（重复消息安全）
        if (CouponStatus.USED.name().equals(c.getStatus())) {
            if (orderNo.equals(c.getUsedOrderNo())) {
                log.info("[COUPON] 重复核销已忽略 couponNo={} orderNo={}", couponNo, orderNo);
                return true;
            }
            throw new BizException(ResultCode.BAD_REQUEST, "该券已被其他订单使用");
        }
        if (!CouponStatus.LOCKED.name().equals(c.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "券状态为 " + c.getStatus() + "，未锁定不可核销");
        }
        if (!orderNo.equals(c.getLockedOrderNo())) {
            throw new BizException(ResultCode.BAD_REQUEST, "该券不是被当前订单锁定的");
        }

        int rows = couponMapper.tryUse(c.getId(), orderNo, discountAmount);
        if (rows == 0) {
            MktCoupon again = couponMapper.selectByNo(couponNo);
            if (again != null && CouponStatus.USED.name().equals(again.getStatus())
                    && orderNo.equals(again.getUsedOrderNo())) {
                return true;
            }
            throw new BizException(ResultCode.BAD_REQUEST, "优惠券核销失败");
        }
        writeLog(c, CouponLogAction.USE, orderNo, discountAmount,
                CouponStatus.LOCKED.name(), CouponStatus.USED.name());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean releaseByOrder(String orderNo) {
        // 先查出被释放的券，好清理对应的 Redis 锁
        List<MktCoupon> locked = couponMapper.selectList(
                Wrappers.<MktCoupon>lambdaQuery()
                        .eq(MktCoupon::getLockedOrderNo, orderNo)
                        .eq(MktCoupon::getStatus, CouponStatus.LOCKED.name()));
        int rows = couponMapper.releaseByOrderNo(orderNo);
        if (rows > 0) {
            for (MktCoupon c : locked) {
                redis.delete(REDIS_LOCK_PREFIX + c.getCouponNo() + ":" + orderNo);
                writeLog(c, CouponLogAction.RELEASE, orderNo, null,
                        CouponStatus.LOCKED.name(), CouponStatus.UNUSED.name());
            }
            log.info("[COUPON] 按订单释放券成功 orderNo={} rows={}", orderNo, rows);
            return true;
        }
        log.debug("[COUPON] 按订单释放券：没有锁定中的券 orderNo={}", orderNo);
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int expireBatch(int limit) {
        int n = couponMapper.expireBatch(LocalDateTime.now(), limit <= 0 ? 200 : limit);
        if (n > 0) {
            log.info("[COUPON] 过期处理完成 count={}", n);
        }
        return n;
    }

    // ==================== 工具 ====================

    private void writeLog(MktCoupon c, CouponLogAction action, String orderNo, Long amount,
                          String from, String to) {
        MktCouponLog l = new MktCouponLog();
        l.setBrandId(c.getBrandId());
        l.setCouponId(c.getId());
        l.setCouponNo(c.getCouponNo());
        l.setMemberId(c.getMemberId());
        l.setAction(action.name());
        l.setOrderNo(orderNo);
        l.setAmount(amount);
        l.setFromStatus(from);
        l.setToStatus(to);
        logMapper.insert(l);
    }

    private CouponVO toVO(MktCoupon c, String couponName) {
        // 券名来自模板；为 null 时回查一次，避免前端显示空白
        if (couponName == null && c.getTemplateId() != null) {
            MktCouponTemplate tpl = templateMapper.selectById(c.getTemplateId());
            if (tpl != null) {
                couponName = tpl.getCouponName();
            }
        }
        CouponVO vo = new CouponVO();
        vo.setId(c.getId());
        vo.setCouponNo(c.getCouponNo());
        vo.setTemplateId(c.getTemplateId());
        vo.setCouponName(couponName);
        vo.setCouponType(c.getCouponType());
        try {
            vo.setCouponTypeLabel(CouponType.valueOf(c.getCouponType()).getLabel());
        } catch (Exception e) {
            vo.setCouponTypeLabel(c.getCouponType());
        }
        vo.setFaceValue(c.getFaceValue());
        vo.setDiscountRate(c.getDiscountRate());
        vo.setMaxDiscountAmount(c.getMaxDiscountAmount());
        vo.setThresholdAmount(c.getThresholdAmount());
        vo.setScopeType(c.getScopeType());
        vo.setStatus(c.getStatus());
        try {
            vo.setStatusLabel(CouponStatus.valueOf(c.getStatus()).getLabel());
        } catch (Exception e) {
            vo.setStatusLabel(c.getStatus());
        }
        vo.setObtainType(c.getObtainType());
        vo.setObtainTime(c.getObtainTime());
        vo.setValidStart(c.getValidStart());
        vo.setValidEnd(c.getValidEnd());
        vo.setLockedOrderNo(c.getLockedOrderNo());
        vo.setUsedOrderNo(c.getUsedOrderNo());
        vo.setDiscountAmount(c.getDiscountAmount());
        LocalDateTime now = LocalDateTime.now();
        vo.setUsable(CouponStatus.UNUSED.name().equals(c.getStatus())
                && !now.isBefore(c.getValidStart()) && !now.isAfter(c.getValidEnd()));
        return vo;
    }
}
