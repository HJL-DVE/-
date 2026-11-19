# TeaClub 第五阶段·任务四补充：ORDER_PAID 真实消费者接入与消费幂等

| 项目 | 内容 |
| --- | --- |
| 文档编号 | TC-T4-08 |
| 日期 | 2026-11-20 |
| 目的 | 给 `ORDER_PAID` 接一个真实消费者，把会员消费档案从「同步 Feign 累加」改成「事件驱动累加」 |
| 结论 | 已完成，端到端实测 14/14 + 5/5 全部通过 |

---

## 一、改了什么（三件事）

| # | 变化 | 说明 |
| --- | --- | --- |
| 1 | 新增 `OrderPaidConsumer` | member-service 真正订阅 `order.paid` 队列执行业务 |
| 2 | 新增 `MemberConsumeStatsUpdater` | Inbox 认领 + 累加在同一本地事务 |
| 3 | 删除 order-service 的同步 Feign 累加 | `OrderServiceImpl.updateMemberStats` 与 `MemberInternalClient.updateConsumeStats` 全部移除 |

改之前是「订单支付成功 -> 订单服务同步调会员服务内部接口改档案」：
两个服务用 HTTP 强耦合，会员服务一挂，订单支付就跟着失败或带脏数据。
改之后是「订单支付成功 -> 本地事务写订单 + 写 outbox -> Relay 投递 -> 会员服务消费累加」，
支付链路不再依赖会员服务可用性。

---

## 二、链路

```text
order-service  payByBalance / processPaySuccess
  └─ 同一本地事务：UPDATE oms_order + INSERT sys_outbox_message(ORDER_PAID)   [MANDATORY 传播]
       ↓
  OutboxRelay（每 1 秒轮询 PENDING）
  └─ 投递 teaclub.business.exchange / routing_key=order.paid
       ↓
  teaclub.member.order-paid.queue（DLQ: ...queue.dlq）
       ↓
  member-service  OrderPaidConsumer
  ├─ 第一层：Redis MessageIdempotencyGuard  mq:consumed:{eventId}（TTL 72h）
  ├─ 第二层：sys_inbox_message 按 event_id 唯一键认领（与业务更新同事务）
  └─ MemberConsumeStatsUpdater.applyOrderPaid
       UPDATE mem_member
         SET total_consume_amount = COALESCE(total_consume_amount,0) + #{amount},
             total_order_count    = COALESCE(total_order_count,0) + 1,
             last_consume_time    = NOW(3)
       WHERE id = #{memberId} AND deleted = 0
```

`applyOrderPaid` 标注 `@Transactional`，**Inbox 认领与业务更新处于同一事务**，
所以「认领成功但更新失败」不会发生；「更新成功但认领未提交」也不会发生。
重复事件在 `sys_inbox_message` 的 `event_id` 唯一键上抛 `DuplicateKeyException`，
被 `ConsumerInbox.tryClaim` 捕获后返回 false，消费者直接 return，业务更新根本不执行。

---

## 三、为什么不用「消费端只做 Redis 去重」

| 层 | 组件 | 失效场景 | 兜底者 |
| --- | --- | --- | --- |
| 第一层 | Redis `mq:consumed:{eventId}` | Redis 重启 / key 过期 / 被误删 | 第二层 |
| 第二层 | `sys_inbox_message.event_id` 唯一键 | 数据库回滚 / 同事务异常 | 第三层 |
| 第三层 | 业务侧幂等（`uk_txn_biz` 等） | — | 最终防线 |

本次专门实测了**第一层失效后第二层仍然拦住**（见第六节场景 B）。

---

## 四、涉及文件

```text
新增
  member-service/.../consumer/OrderPaidConsumer.java
  member-service/.../service/MemberConsumeStatsUpdater.java

修改
  common/common-messaging/.../constant/MqConstants.java        新增 RK_ORDER_PAID / Q_MEMBER_ORDER_PAID
  common/common-messaging/.../config/RabbitMqConfig.java       队列 + DLQ + 2 个绑定；消息转换器改为 INFERRED + trusted *
  common/common-messaging/.../outbox/OutboxRelay.java          发送 JsonNode 而非 String（修双重序列化）
  common/common-messaging/.../consumer/AbstractEventConsumer.java  解包 textual 节点
  member-service/.../controller/MemberInternalController.java  /consume-stats 标记 @Deprecated
  order-service/.../service/impl/OrderServiceImpl.java         删除 updateMemberStats 两处调用与方法体
  order-service/.../client/MemberInternalClient.java           删除 updateConsumeStats 声明
```

### 顺带修掉的两个真实缺陷

| 缺陷 | 现象 | 修复 |
| --- | --- | --- |
| Outbox 事件双重序列化 | 消费端 `Cannot construct instance of EventMessage (no String-argument constructor)` | Relay 改为发送 `JsonNode`；基类解包 textual 节点 |
| 消息转换器信任包过窄 | `The class 'ObjectNode' is not in the trusted packages: [java.util, java.lang]` | `DefaultJackson2JavaTypeMapper` + `TrustedPackages("*")` + `TypePrecedence.INFERRED` |

这两个不修，任何消费者都跑不起来，属于「接消费者」暴露出的既有问题。

---

## 五、端到端实测（脚本 15）

```text
=== 0. 源码校验：同步 Feign 累加已移除 ===
  [PASS] OrderServiceImpl 无 updateMemberStats 调用        grep 结果=0
  [PASS] MemberInternalClient 无 updateConsumeStats 声明   grep 结果=0
=== 1. 清空会员消费队列（保证基线干净）===  purge status=204
=== 2. 事件驱动累加会员消费档案 ===
  支付前: 累计消费=71430 订单数=19
  [PASS] 余额支付成功 orderNo=TC20261120010034000002 应付=1715
  [PASS] 消费档案由事件累加 累计消费 71430 -> 73145（应 +1715）
  [PASS] 订单数 +1 订单数 19 -> 20
  端到端时延约 1 秒（Outbox 1s 轮询 + MQ 投递 + 消费）
=== 3. Inbox 幂等记录已落库 ===
  [PASS] 订单事件已生成 eventId=9f06cbd03da7478492179459987889b6
  [PASS] Inbox 已认领该事件 inbox=... ORDER_PAID
=== 4. 重复投递（同一 eventId 再发一次）===
  [PASS] 重复消息已成功投递到 Broker status=200 routed=true
  [PASS] 重复投递未重复累加 累计 73145 -> 73145，订单数 20 -> 20
=== 5. 连发 5 次重复投递 ===
  [PASS] 5 次重复投递仍未重复累加 累计=73145
=== 6. 队列与消费端状态 ===
  [PASS] 会员消费队列存在 messages=0 consumers=1
  [PASS] 队列已积压清零 messages=0
  [PASS] 存在活跃消费者 consumers=1
  [PASS] 死信队列为空 dlq.messages=0
=== 汇总: PASS=14 FAIL=0 ===
```

第二次整套复跑（DLQ 名称修正后）：`PASS=14 FAIL=0`，
累计消费 73145 -> 74860（+1715），订单数 20 -> 21，时延仍约 1 秒。

关键点：**重复消息是真的投进了 Broker**（`routed=true`），
不是脚本自己拦掉的；拦住它的是消费者与数据库。

服务端日志同时可见两层拦截：

```text
MessageIdempotencyGuard - [MQ] 重复消息已忽略 eventId=9f06cbd0...
OrderPaidConsumer      - 重复消息已忽略 eventId=9f06cbd0...
```

---

## 六、第二层幂等实测（脚本 16）

场景设计：**手动删掉 Redis 的第一层幂等键**，让第一层彻底失效，再看数据库能不能兜住。

```text
目标事件 eventId=9f06cbd03da7478492179459987889b6 bizNo=TC20261120010034000002
当前累计消费=73145 订单数=20
=== 场景 A：清空 Redis 幂等键，验证第二层（DB Inbox）兜底 ===
  Redis DEL 结果=1
  [PASS] Redis 键已清除 EXISTS=0
  [PASS] 消息已投递 routed=true
  [PASS] 数据库 Inbox 拦截了重复累加 累计消费 73145 -> 73145，订单数 20 -> 20
  [PASS] Inbox 仍只有 1 条记录 count=1
=== 场景 B：Redis 键已被消费者重新写回（第一层恢复） ===
  [PASS] Redis 幂等键已重新建立 TTL=259194 秒
=== 汇总: PASS=5 FAIL=0 ===
```

结论：`sys_inbox_message` 唯一键这一层是**独立生效**的，
不是「因为有 Redis 所以看着像幂等」。

---

## 七、对既有结论的影响

| 项 | 变化 |
| --- | --- |
| 会员 `total_consume_amount` / `total_order_count` | 数据一字不差，只是写入方从 HTTP 同步变成 MQ 异步 |
| 订单支付链路 | 不再依赖 member-service 可用性（member 挂掉，支付照常成功） |
| `POST /api/member/internal/consume-stats` | 保留但标记 `@Deprecated`，仅作排障用途 |
| 资金不变量 I1–I6 | 不受影响（成员档案不是资金口径），复跑钱包对账 `issues=0` |
| 跨服务对账 | 复跑 `checked=10 issues=0 passed=true` |

---

## 八、仍未覆盖（如实标注）

| 项 | 说明 |
| --- | --- |
| `ORDER_PAID` 两个发事件点未合并 | 余额支付与微信回写各发一次，同订单号仅一处会触发，但语义上应收敛为一个 |
| 其余 7 类事件无消费者 | `RECHARGE_PAID` / `WALLET_DEDUCTED` / `WALLET_FROZEN` / `PAYMENT_SUCCEEDED` / `REFUND_SUCCEEDED` 等仍只有生产端 |
| 消费端无重试上限治理 | 当前依赖 Spring AMQP 默认重试 + DLQ，未做分级退避 |
| 多实例消费顺序 | 同一会员的并发订单事件不保证顺序，累加语义（`+= `）本身与顺序无关，但若将来改「覆盖写」需补分区键 |

---

## 九、运行方式

```bash
node backend/infrastructure/test/15-order-paid-consumer.mjs
node backend/infrastructure/test/16-consumer-inbox-2nd-layer.mjs
```

脚本 15 会真实创建并支付一笔订单（产生真实资金流水），
脚本 16 会删除目标事件的 Redis 幂等键以验证第二层兜底。
