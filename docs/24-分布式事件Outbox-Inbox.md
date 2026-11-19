# TeaClub 第五阶段·任务三：分布式事件机制（Outbox / Inbox）

| 项目 | 内容 |
| --- | --- |
| 文档编号 | TC-MSA-18 |
| 日期 | 2026-11-19 |
| 范围 | Transactional Outbox + Consumer Inbox + 8 类领域事件 |
| 状态 | 实测通过（事件登记 → Relay 投递 → SENT） |

---

## 1. 实现内容

### 1.1 8 类领域事件（DomainEvents）
MEMBER_REGISTERED / RECHARGE_PAID / WALLET_FROZEN / WALLET_DEDUCTED /
PAYMENT_SUCCEEDED / PAYMENT_FAILED / REFUND_SUCCEEDED / ORDER_PAID

每个事件包含：event_id（唯一）、event_type、biz_no（业务单号）、version（事件版本）、
occurred_at（产生时间）、trace_id（链路 ID）—— 由 EventMessage 信封统一提供。

### 1.2 Transactional Outbox

- OutboxMessage 实体 + OutboxStore 接口 + JdbcOutboxStore（各服务库）
- OutboxService.enqueue()：业务事务内登记事件（PROPAGATION_MANDATORY）
- OutboxRelay：定时扫描（默认 1s）未投递消息，发送到 RabbitMQ topic 交换机
- 投递成功标记 SENT，失败标记 FAILED 并累加 attempts
- 至少一次投递语义：消费端必须幂等

### 1.3 Consumer Inbox

- ConsumerInbox.tryClaim()：基于数据库主键唯一索引去重
- 与 Redis MessageIdempotencyGuard 构成双层幂等
- 两者都不替代资金业务本身的唯一索引幂等

— 建表脚本：common-messaging/db/outbox-inbox.sql，已部署到 6 个服务库

---

## 2. 实测结果

1. 调用 /api/order/internal/outbox-demo/enqueue
   → eventId=3618a59325d04069b8bd6e2df44a5a49, eventType=ORDER_PAID, routingKey=order.paid
2. 3 秒后查询 pending → 0（Relay 已投递）
3. 数据库 sys_outbox_message 状态 → SENT=1

---

## 3. 一致性设计

跨服务一致性采用「本地事务 + Outbox + Saga 补偿」，不采用直接跨库修改数据：
- 业务数据与 outbox 记录在同一本地事务提交
- Relay 异步投递，业务方法不阻塞等待 RabbitMQ
- 消费端通过 Inbox 幂等去重，重复投递不会重复处理

---

## 4. 未完成项（如实标注）

1. Saga 补偿编排器（8 类事件的补偿动作未实现）
2. 各业务服务在真实业务方法中调用 OutboxService（当前只有演示端点）
3. OutboxRelay 的死信转移与告警
4. 事件消费端 Inbox 的实际接入（组件就绪，未接业务消费者）
5. 单元测试（本轮只做端到端）

> 不能声称完成：以上 5 项均未实际验证。
