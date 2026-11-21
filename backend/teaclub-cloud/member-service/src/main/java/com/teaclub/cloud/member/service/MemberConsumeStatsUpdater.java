package com.teaclub.cloud.member.service;

import com.teaclub.cloud.messaging.inbox.ConsumerInbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 会员消费档案更新器（事件驱动）。
 *
 * <p>由 {@code ORDER_PAID} 事件触发，取代原先 order-service 的同步 Feign 调用。
 *
 * <p><b>幂等三层</b>：
 * <ol>
 *   <li>Redis 预筛（AbstractEventConsumer 内）</li>
 *   <li><b>数据库 Inbox</b>：sys_inbox_message 主键插入，与业务更新<b>同一事务</b></li>
 *   <li>业务侧仅做增量 UPDATE，不做"先查后改"，天然可重试</li>
 * </ol>
 *
 * <p>为什么 Inbox 必须与业务更新同事务：
 * 若 Inbox 先提交、业务失败，这条消息就永远被判定为"已处理"而丢失；
 * 同事务则要么都成功，要么都回滚等待重投。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberConsumeStatsUpdater {

    private final JdbcTemplate jdbcTemplate;
    private final ConsumerInbox inbox;

    /**
     * 应用一次订单已支付事件。
     *
     * @return true 表示本次真实处理；false 表示重复投递已忽略
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean applyOrderPaid(String eventId, String eventType, String traceId,
                                  Map<String, Object> payload) {
        // ---- 幂等第一关：数据库 Inbox（与后续 UPDATE 同一事务）----
        if (eventId != null && !inbox.tryClaim(eventId, eventType, traceId)) {
            log.info("[ORDER_PAID] 重复投递已忽略 eventId={}", eventId);
            return false;
        }

        Long memberId = toLong(payload.get("memberId"));
        Long paidAmount = toLong(payload.get("paidAmount"));
        String orderNo = payload.get("orderNo") == null ? null : String.valueOf(payload.get("orderNo"));

        if (memberId == null) {
            // 散客订单：无会员，不需要更新档案
            log.debug("[ORDER_PAID] 散客订单，跳过会员档案 orderNo={}", orderNo);
            return true;
        }
        if (paidAmount == null || paidAmount <= 0) {
            log.warn("[ORDER_PAID] 支付金额异常，跳过 orderNo={} paidAmount={}", orderNo, paidAmount);
            return true;
        }

        int rows = jdbcTemplate.update(
                "UPDATE mem_member SET "
                        + "total_consume_amount = COALESCE(total_consume_amount,0) + ?, "
                        + "total_order_count = COALESCE(total_order_count,0) + 1, "
                        + "last_consume_time = NOW(3) "
                        + "WHERE id = ? AND deleted = 0",
                paidAmount, memberId);

        if (rows == 0) {
            // 会员不存在：不抛异常（避免无效重试），但要留痕
            log.warn("[ORDER_PAID] 会员不存在，未更新档案 memberId={} orderNo={}", memberId, orderNo);
        } else {
            log.info("[ORDER_PAID] 会员消费档案已更新 memberId={} +{} 分 orderNo={} eventId={}",
                    memberId, paidAmount, orderNo, eventId);
        }

        // ---- 等级自动升级：累计消费变化后重算（只升不降）----
        try {
            upgradeLevel(memberId);
        } catch (Exception e) {
            // 升级失败不影响消费档案（档案是资金事实，等级是派生结果）
            log.error("[ORDER_PAID] 等级重算失败 memberId={}", memberId, e);
        }

        return true;
    }

    /**
     * 按累计消费金额重算会员等级（<b>只升不降</b>）。
     *
     * <p>规则：取所有 {@code upgrade_type=CONSUME_AMOUNT} 且 {@code upgrade_threshold <= 累计消费}
     * 的启用等级中 level_no 最大的一个；若大于当前等级才更新。
     *
     * <p>只升不降：退款不会降级（避免会员感知突变），如需降级应单独做运营策略。
     */
    private void upgradeLevel(Long memberId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT level_id, total_consume_amount FROM mem_member WHERE id = ? AND deleted = 0",
                memberId);
        Object currentLevelId = row.get("level_id");
        long consume = row.get("total_consume_amount") instanceof Number n ? n.longValue() : 0L;

        // 命中的最高等级
        java.util.List<Map<String, Object>> hit = jdbcTemplate.queryForList(
                "SELECT l.id, l.level_no FROM mem_member_level l "
                        + "WHERE l.deleted = 0 AND l.status = 'ENABLED' "
                        + "  AND l.upgrade_type = 'CONSUME_AMOUNT' "
                        + "  AND l.brand_id = (SELECT brand_id FROM mem_member WHERE id = ?) "
                        + "  AND l.upgrade_threshold <= ? "
                        + "ORDER BY l.level_no DESC LIMIT 1",
                memberId, consume);
        if (hit.isEmpty()) {
            return;
        }
        Long targetId = ((Number) hit.get(0).get("id")).longValue();
        int targetNo = ((Number) hit.get(0).get("level_no")).intValue();

        if (currentLevelId != null && ((Number) currentLevelId).longValue() == targetId) {
            return;
        }
        // 当前等级序号
        Integer currentNo = null;
        if (currentLevelId != null) {
            java.util.List<Integer> nos = jdbcTemplate.queryForList(
                    "SELECT level_no FROM mem_member_level WHERE id = ?", Integer.class,
                    ((Number) currentLevelId).longValue());
            currentNo = nos.isEmpty() ? null : nos.get(0);
        }
        if (currentNo != null && targetNo <= currentNo) {
            // 目标等级不高于当前 → 不降级
            return;
        }
        jdbcTemplate.update("UPDATE mem_member SET level_id = ? WHERE id = ?", targetId, memberId);
        log.info("[LEVEL-UP] 会员升级 memberId={} 累计消费={}分 等级 {} -> {}",
                memberId, consume, currentNo == null ? "-" : String.valueOf(currentNo), targetNo);
    }


    private static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
