# 任务四 资金一致性测试脚本

真实端到端集成测试脚本，全部直接打容器内服务（经 Gateway 或服务内部接口），
不 mock 业务逻辑，结果来自真实数据库与真实 HTTP 响应。

## 前置

```bash
cd backend/infrastructure/docker
docker compose -f docker-compose.yml --env-file .env.docker up -d
```

脚本读取 `backend/infrastructure/docker/.env.docker` 获取 `INTERNAL_TOKEN` 与数据库口令，
需要 Node 18+（使用内置 fetch）。

## 脚本

| 脚本 | 覆盖 | 对应报告 |
| --- | --- | --- |
| 01-wallet-freeze-lifecycle | 冻结 / 确认扣款 / 释放 / 余额不足 | docs/25 |
| 02-wallet-concurrency | 20 并发扣款、30 并发同单号、20 并发超余额 | docs/26 |
| 03-recharge-consistency | 充值单→支付→通知→入账→重复通知 | docs/25 |
| 04-order-wallet-payment | 订单钱包支付 10 场景 | docs/25 |
| 05-service-outage-recovery | 真实停止钱包容器并恢复 | docs/30 |
| 06-payment-scenarios | 支付 11 场景 | docs/27 |
| 07-payment-notify-verification | AppID / 商户号 / 验签校验 | docs/27 |
| 08-refund-flow | 退款申请→平台确认→订单状态 | docs/28 |
| 09-refund-duplicate-guard | 重复退款必须被拒 | docs/28 |
| 10-saga-compensation-reconcile | Saga 补偿 + 对账 | docs/29、docs/30 |
| 11-final-evidence | 四条链路真实凭证快照 | docs/25 |
| 12-fund-invariants | 资金不变量 I1–I6 | docs/25 |
| 13-partial-freeze | 冻结部分确认 + 余额释放 | docs/31 |
| 14-timeout-close-events-reconcile | 超时关单 + Outbox 事件 + 跨服务对账 | docs/31 |
| 15-order-paid-consumer | ORDER_PAID 事件驱动累加会员档案 + 重复投递幂等 | docs/32 |
| 16-consumer-inbox-2nd-layer | 删 Redis 幂等键，验证 DB Inbox 第二层兜底 | docs/32 |
| 17-phase6-smoke | 第六阶段冒烟：管理端/钱包管理端/统计/会员码/匿名浏览/微信登录/会员端/令牌隔离 | docs/36 |
| 18-recharge-pay-closed-loop | 充值→支付→入账→幂等→下单→钱包支付 全闭环 | docs/36 |
| 19-refund-via-gateway | 微信支付→申请退款→重复退款被拒→退款回调→订单 REFUNDED | docs/36 |
| 20-payment-channel | 支付渠道默认值/显式覆盖/非法拒绝/无 openid 降级 | docs/37 |

## 一键回归门禁

```bash
node backend/infrastructure/test/run-regression.mjs
```

顺序跑 17 / 18 / 19 / 20 / 12，任一失败即整体失败（退出码 1）；
网关未就绪会先等待 60 秒并提示容器启动命令。当前：**脚本 5/5，断言 71/71**。
支持过滤：`run-regression.mjs smoke` / `run-regression.mjs 17 18`。

## 运行

```bash
node backend/infrastructure/test/02-wallet-concurrency.mjs
```

> 注意：部分脚本使用唯一会员号避免历史数据干扰；
> 若断言「余额 +N」失败，先确认该会员是否已有历史余额。

## 单元测试

```bash
cd backend/teaclub-cloud
mvn -pl common/common-messaging,payment-service -am test \
  -Dtest="DomainEventsTest,RefundAllocationTest,MockNotifySignatureVerifierTest" \
  -Dsurefire.failIfNoSpecifiedTests=false
```

覆盖：事件路由键、退款资金分摊、支付通知身份校验（15 个用例）。
