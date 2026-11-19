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
        return true;
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
