package com.teaclub.cloud.messaging.outbox;

import java.util.List;

/**
 * Outbox 存储抽象。
 *
 * <p>各服务用自己的数据源实现（ JDBC / MyBatis-Plus ），
 * 保证「业务数据 + outbox 记录」在同一个本地事务内提交。
 */
public interface OutboxStore {

    /**
     * 保存待发送消息（必须与业务操作在相同事务内调用）。
     */
    void save(OutboxMessage message);

    /**
     * 扫描待投递消息。
     *
     * @param limit 最多扫描条数
     */
    List<OutboxMessage> findPending(int limit);

    /**
     * 标记为已投递。
     */
    void markSent(String eventId);

    /**
     * 记录投递失败（供重试/告警）。
     *
     * @param eventId 事件 ID
     * @param error   错误信息
     */
    void markFailed(String eventId, String error);
}
