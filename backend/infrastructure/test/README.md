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

## 运行

```bash
node backend/infrastructure/test/02-wallet-concurrency.mjs
```

> 注意：部分脚本使用唯一会员号避免历史数据干扰；
> 若断言「余额 +N」失败，先确认该会员是否已有历史余额。
