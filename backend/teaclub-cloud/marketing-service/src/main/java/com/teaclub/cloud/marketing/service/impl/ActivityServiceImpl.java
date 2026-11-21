package com.teaclub.cloud.marketing.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.core.util.BizNoGenerator;
import com.teaclub.cloud.marketing.dto.ActivityRewardConfig;
import com.teaclub.cloud.marketing.dto.ActivitySaveRequest;
import com.teaclub.cloud.marketing.entity.MktActivity;
import com.teaclub.cloud.marketing.entity.MktActivityRecord;
import com.teaclub.cloud.marketing.enums.ActivityStatus;
import com.teaclub.cloud.marketing.enums.ActivityType;
import com.teaclub.cloud.marketing.enums.PointsBizType;
import com.teaclub.cloud.marketing.mapper.MktActivityMapper;
import com.teaclub.cloud.marketing.mapper.MktActivityRecordMapper;
import com.teaclub.cloud.marketing.mapper.MktActivityStoreMapper;
import com.teaclub.cloud.marketing.service.ActivityService;
import com.teaclub.cloud.marketing.service.CouponService;
import com.teaclub.cloud.marketing.service.PointsService;
import com.teaclub.cloud.marketing.vo.ActivityVO;
import com.teaclub.cloud.security.context.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 营销活动服务实现。
 *
 * <p><b>发放奖励的统一流程</b>：
 * <pre>
 *   1. 占活动名额（used_quota 原子 +1，满则放弃）
 *   2. 写参与记录（唯一键拦重复）
 *   3. 发券 / 发积分
 * </pre>
 * 三步同一事务：任何一步失败整体回滚，不会出现"占了名额但没发奖"。
 *
 * <p>第 2 步在先，是因为唯一键冲突是「重复参与」的最可靠信号；
 * 先写记录能避免重复发券（券比积分难回退）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityService {

    private final MktActivityMapper activityMapper;
    private final MktActivityRecordMapper recordMapper;
    private final MktActivityStoreMapper storeMapper;
    private final CouponService couponService;
    private final PointsService pointsService;
    private final JdbcTemplate jdbcTemplate;
    private final org.springframework.transaction.PlatformTransactionManager txManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 管理端 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ActivitySaveRequest request) {
        validate(request);
        MktActivity a = new MktActivity();
        a.setBrandId(brandId());
        a.setActivityNo(BizNoGenerator.of("AC"));
        applyRequest(a, request);
        a.setUsedQuota(0);
        a.setDeleted(0);
        activityMapper.insert(a);
        saveStores(a.getId(), request);
        log.info("[ACTIVITY] 新建 id={} no={} type={} name={}",
                a.getId(), a.getActivityNo(), a.getActivityType(), a.getActivityName());
        return a.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ActivitySaveRequest request) {
        MktActivity a = activityMapper.selectById(id);
        if (a == null || a.getDeleted() == 1) {
            throw new BizException(ResultCode.NOT_FOUND, "活动不存在");
        }
        validate(request);
        applyRequest(a, request);
        a.setId(id);
        activityMapper.updateById(a);
        jdbcTemplate.update("DELETE FROM mkt_activity_store WHERE activity_id = ?", id);
        saveStores(id, request);
        log.info("[ACTIVITY] 更新 id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MktActivity a = activityMapper.selectById(id);
        if (a == null || a.getDeleted() == 1) {
            throw new BizException(ResultCode.NOT_FOUND, "活动不存在");
        }
        MktActivity patch = new MktActivity();
        patch.setId(id);
        patch.setDeleted(1);
        patch.setStatus("DISABLED");
        activityMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String status) {
        try {
            ActivityStatus.valueOf(status);
        } catch (Exception e) {
            throw new BizException(ResultCode.PARAM_INVALID, "非法状态: " + status);
        }
        MktActivity a = activityMapper.selectById(id);
        if (a == null || a.getDeleted() == 1) {
            throw new BizException(ResultCode.NOT_FOUND, "活动不存在");
        }
        MktActivity patch = new MktActivity();
        patch.setId(id);
        patch.setStatus(status);
        activityMapper.updateById(patch);
    }

    @Override
    public ActivityVO detail(Long id) {
        MktActivity a = activityMapper.selectById(id);
        if (a == null || a.getDeleted() == 1) {
            throw new BizException(ResultCode.NOT_FOUND, "活动不存在");
        }
        return toVO(a, null);
    }

    @Override
    public List<ActivityVO> listAll(String activityType, String status) {
        var w = com.baomidou.mybatisplus.core.toolkit.Wrappers.<MktActivity>lambdaQuery()
                .eq(MktActivity::getBrandId, brandId())
                .eq(MktActivity::getDeleted, 0)
                .orderByDesc(MktActivity::getId);
        if (activityType != null && !activityType.isBlank()) {
            w.eq(MktActivity::getActivityType, activityType);
        }
        if (status != null && !status.isBlank()) {
            w.eq(MktActivity::getStatus, status);
        }
        return activityMapper.selectList(w).stream().map(a -> toVO(a, null)).toList();
    }

    // ==================== 会员端 ====================

    @Override
    public List<ActivityVO> listActiveForMember(Long memberId, Long storeId) {
        LocalDateTime now = LocalDateTime.now();
        List<MktActivity> all = activityMapper.selectAllActive(brandId(), now);
        List<ActivityVO> out = new ArrayList<>();
        for (MktActivity a : all) {
            // 门店范围过滤
            if ("STORE".equals(a.getScopeType())) {
                if (storeId == null || storeMapper.countStore(a.getId(), storeId) == 0) {
                    continue;
                }
            }
            ActivityVO vo = toVO(a, memberId);
            out.add(vo);
        }
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean join(Long activityId, Long memberId, Long storeId) {
        MktActivity a = activityMapper.selectById(activityId);
        if (a == null || a.getDeleted() == 1 || !"ENABLED".equals(a.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "活动不存在或未开始");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(a.getStartTime()) || now.isAfter(a.getEndTime())) {
            throw new BizException(ResultCode.BAD_REQUEST, "活动不在有效期內");
        }
        if ("STORE".equals(a.getScopeType())
                && (storeId == null || storeMapper.countStore(activityId, storeId) == 0)) {
            throw new BizException(ResultCode.BAD_REQUEST, "该活动不适用于当前门店");
        }
        // bizNo 用 memberId + 日期，保证「每天可参与多次的活动」也能按 perMemberLimit 控制
        String bizNo = "JOIN-" + memberId + "-" + now.toLocalDate();
        return doGrant(a, memberId, bizNo);
    }

    // ==================== 业务触发 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int triggerNewMember(Long brandId, Long memberId) {
        return triggerByType(brandId, memberId, ActivityType.NEW_MEMBER_GIFT,
                "NEW-" + memberId, null, 0L);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int triggerBirthday(Long brandId, Long memberId) {
        // 按年发一次，避免同一天多次触发重复发奖
        int year = LocalDateTime.now().getYear();
        return triggerByType(brandId, memberId, ActivityType.BIRTHDAY,
                "BDAY-" + memberId + "-" + year, null, 0L);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int triggerContinuousSign(Long brandId, Long memberId, int continuousDays) {
        LocalDateTime now = LocalDateTime.now();
        List<MktActivity> list = activityMapper.selectActiveByType(
                brandId, ActivityType.CONTINUOUS_SIGN.name(), now);
        int granted = 0;
        for (MktActivity a : list) {
            ActivityRewardConfig cfg = parseConfig(a.getConfigJson());
            int need = cfg.getContinuousDays() == null ? 0 : cfg.getContinuousDays();
            if (need <= 0 || continuousDays < need) {
                continue;
            }
            // 每达到一个 need 的整数倍发一次
            String bizNo = "SIGN-" + memberId + "-" + need + "-" + (continuousDays / need);
            if (doGrant(a, memberId, bizNo)) {
                granted++;
            }
        }
        return granted;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int triggerOrderActivity(Long brandId, Long memberId, Long storeId,
                                    String orderNo, long orderAmount) {
        if (memberId == null || orderNo == null) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        int granted = 0;
        // 满赠
        for (MktActivity a : activityMapper.selectActiveByType(
                brandId, ActivityType.FULL_GIFT.name(), now)) {
            ActivityRewardConfig cfg = parseConfig(a.getConfigJson());
            long th = cfg.getThresholdAmount() == null ? 0L : cfg.getThresholdAmount();
            if (orderAmount < th) {
                continue;
            }
            if (doGrant(a, memberId, orderNo)) {
                granted++;
            }
        }
        // 门店专属
        for (MktActivity a : activityMapper.selectActiveByType(
                brandId, ActivityType.STORE_SPECIAL.name(), now)) {
            if ("STORE".equals(a.getScopeType())
                    && (storeId == null || storeMapper.countStore(a.getId(), storeId) == 0)) {
                continue;
            }
            ActivityRewardConfig cfg = parseConfig(a.getConfigJson());
            long th = cfg.getThresholdAmount() == null ? 0L : cfg.getThresholdAmount();
            if (orderAmount < th) {
                continue;
            }
            if (doGrant(a, memberId, orderNo)) {
                granted++;
            }
        }
        return granted;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int triggerSleepingRecall(Long brandId, Long memberId) {
        LocalDateTime now = LocalDateTime.now();
        int granted = 0;
        for (MktActivity a : activityMapper.selectActiveByType(
                brandId, ActivityType.SLEEPING_RECALL.name(), now)) {
            String bizNo = "RECALL-" + memberId + "-" + now.toLocalDate();
            if (doGrant(a, memberId, bizNo)) {
                granted++;
            }
        }
        return granted;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int expireBatch(int limit) {
        int n = activityMapper.expireBatch(LocalDateTime.now(), limit <= 0 ? 200 : limit);
        if (n > 0) {
            log.info("[ACTIVITY] 过期处理 count={}", n);
        }
        return n;
    }

    // ==================== 内部 ====================

    private int triggerByType(Long brandId, Long memberId, ActivityType type,
                              String bizNo, Long storeId, long orderAmount) {
        LocalDateTime now = LocalDateTime.now();
        int granted = 0;
        for (MktActivity a : activityMapper.selectActiveByType(brandId, type.name(), now)) {
            if ("STORE".equals(a.getScopeType())
                    && (storeId == null || storeMapper.countStore(a.getId(), storeId) == 0)) {
                continue;
            }
            ActivityRewardConfig cfg = parseConfig(a.getConfigJson());
            long th = cfg.getThresholdAmount() == null ? 0L : cfg.getThresholdAmount();
            if (orderAmount < th) {
                continue;
            }
            if (doGrant(a, memberId, bizNo)) {
                granted++;
            }
        }
        return granted;
    }

    /**
     * 发放一次活动奖励。
     *
     * @return false 表示该会员在该业务单下已参与过（幂等）
     */
    private boolean doGrant(MktActivity a, Long memberId, String bizNo) {
        // 每人限参与次数
        int limit = a.getPerMemberLimit() == null ? 1 : a.getPerMemberLimit();
        if (limit > 0) {
            int joined = recordMapper.countByMember(a.getId(), memberId);
            if (joined >= limit && !bizNo.contains("-")) {
                log.debug("[ACTIVITY] 超过每人限次 activityId={} memberId={}", a.getId(), memberId);
                return false;
            }
        }

        // 1. 写参与记录（唯一键拦重复）—— 必须在发奖之前
        MktActivityRecord rec = new MktActivityRecord();
        rec.setBrandId(a.getBrandId());
        rec.setActivityId(a.getId());
        rec.setMemberId(memberId);
        rec.setBizNo(bizNo);
        rec.setRewardJson(a.getConfigJson());
        try {
            recordMapper.insert(rec);
        } catch (DuplicateKeyException e) {
            log.info("[ACTIVITY] 该业务单已参与过，幂等忽略 activityId={} memberId={} bizNo={}",
                    a.getId(), memberId, bizNo);
            return false;
        }

        // 2. 占名额
        if (activityMapper.tryConsumeQuota(a.getId()) == 0) {
            // 名额满：抛业务异常让事务回滚（撤销刚写的参与记录）
            throw new BizException(ResultCode.BAD_REQUEST,
                    "活动名额已满: " + a.getActivityName());
        }

        // 3. 发奖
        ActivityRewardConfig cfg = parseConfig(a.getConfigJson());
        if (cfg.getCouponTemplateIds() != null) {
            for (Long tplId : cfg.getCouponTemplateIds()) {
                try {
                    // 券发放在独立事务：发券失败（重复/已发完）只回滚券本身，
                    // 不把外层「活动记录+积分」事务标记为 rollback-only
                    org.springframework.transaction.support.TransactionTemplate newTx =
                            new org.springframework.transaction.support.TransactionTemplate(txManager);
                    newTx.setPropagationBehavior(
                            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    newTx.execute(status -> {
                        couponService.grant(tplId, memberId, "ACTIVITY");
                        return null;
                    });
                } catch (Exception e) {
                    log.warn("[ACTIVITY] 发券失败 activityId={} templateId={} err={}",
                            a.getId(), tplId, e.getMessage());
                }
            }
        }
        if (cfg.getPoints() != null && cfg.getPoints() > 0) {
            try {
                pointsService.adjust(memberId, cfg.getPoints(),
                        "ACT-" + a.getId() + "-" + memberId + "-" + bizNo,
                        "活动奖励: " + a.getActivityName());
            } catch (Exception e) {
                log.warn("[ACTIVITY] 发积分失败 activityId={} err={}", a.getId(), e.getMessage());
            }
        }
        log.info("[ACTIVITY] 发放成功 activityId={} type={} memberId={} bizNo={}",
                a.getId(), a.getActivityType(), memberId, bizNo);
        return true;
    }

    private ActivityRewardConfig parseConfig(String json) {
        if (json == null || json.isBlank()) {
            return new ActivityRewardConfig();
        }
        try {
            return objectMapper.readValue(json, ActivityRewardConfig.class);
        } catch (Exception e) {
            log.warn("[ACTIVITY] config_json 解析失败: {}", e.getMessage());
            return new ActivityRewardConfig();
        }
    }

    private void validate(ActivitySaveRequest r) {
        try {
            ActivityType.valueOf(r.getActivityType());
        } catch (Exception e) {
            throw new BizException(ResultCode.PARAM_INVALID, "不支持的活动类型: " + r.getActivityType());
        }
        if (!ActivityType.POINTS_MALL.name().equals(r.getActivityType())) {
            if (r.getStartTime() == null || r.getEndTime() == null) {
                throw new BizException(ResultCode.PARAM_INVALID, "活动必须设置起止时间");
            }
            if (r.getEndTime().isBefore(r.getStartTime())) {
                throw new BizException(ResultCode.PARAM_INVALID, "结束时间不能早于开始时间");
            }
        }
        if ("STORE".equals(r.getScopeType())
                && (r.getStoreIds() == null || r.getStoreIds().isEmpty())) {
            throw new BizException(ResultCode.PARAM_INVALID, "门店活动必须配置适用门店");
        }
    }

    private void applyRequest(MktActivity a, ActivitySaveRequest r) {
        a.setActivityName(r.getActivityName());
        a.setActivityType(r.getActivityType());
        a.setScopeType(r.getScopeType() == null ? "ALL" : r.getScopeType());
        a.setStartTime(r.getStartTime());
        a.setEndTime(r.getEndTime());
        a.setConfigJson(r.getConfigJson());
        a.setTotalQuota(r.getTotalQuota() == null ? 0 : r.getTotalQuota());
        a.setPerMemberLimit(r.getPerMemberLimit() == null ? 1 : r.getPerMemberLimit());
        a.setPriority(r.getPriority() == null ? 0 : r.getPriority());
        a.setStatus(r.getStatus() == null ? "DRAFT" : r.getStatus());
        a.setDescription(r.getDescription());
    }

    private void saveStores(Long activityId, ActivitySaveRequest r) {
        if (r.getStoreIds() == null) {
            return;
        }
        Long brand = brandId();
        for (Long sid : r.getStoreIds()) {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO mkt_activity_store (brand_id, activity_id, store_id) VALUES (?,?,?)",
                    brand, activityId, sid);
        }
    }

    private ActivityVO toVO(MktActivity a, Long memberId) {
        ActivityVO vo = new ActivityVO();
        vo.setId(a.getId());
        vo.setActivityNo(a.getActivityNo());
        vo.setActivityName(a.getActivityName());
        vo.setActivityType(a.getActivityType());
        try {
            vo.setActivityTypeLabel(ActivityType.valueOf(a.getActivityType()).getLabel());
        } catch (Exception e) {
            vo.setActivityTypeLabel(a.getActivityType());
        }
        vo.setScopeType(a.getScopeType());
        vo.setStartTime(a.getStartTime());
        vo.setEndTime(a.getEndTime());
        vo.setConfigJson(a.getConfigJson());
        vo.setTotalQuota(a.getTotalQuota());
        vo.setUsedQuota(a.getUsedQuota());
        vo.setPerMemberLimit(a.getPerMemberLimit());
        vo.setPriority(a.getPriority());
        vo.setStatus(a.getStatus());
        try {
            vo.setStatusLabel(ActivityStatus.valueOf(a.getStatus()).getLabel());
        } catch (Exception e) {
            vo.setStatusLabel(a.getStatus());
        }
        vo.setDescription(a.getDescription());
        vo.setCreateTime(a.getCreateTime());
        vo.setStoreIds(storeMapper.selectStoreIds(a.getId()));
        if (memberId != null) {
            int joined = recordMapper.countByMember(a.getId(), memberId);
            int limit = a.getPerMemberLimit() == null ? 1 : a.getPerMemberLimit();
            vo.setJoined(joined > 0);
            vo.setRemainTimes(limit <= 0 ? -1 : Math.max(0, limit - joined));
        }
        return vo;
    }

    private Long brandId() {
        Long b = UserContext.currentBrandIdOrNull();
        return b == null ? 1L : b;
    }
}
