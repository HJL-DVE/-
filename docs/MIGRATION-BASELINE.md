# TeaClub 迁移黄金基线快照

| 项目 | 内容 |
| --- | --- |
| 文档编号 | TC-MSA-07 |
| 采集时间 | 2026-11-19 |
| 采集方式 | 实际查询 `teaclub` 库 |
| 用途 | **数据迁移前后必须逐项比对，任一项不等即为迁移失败** |

---

## 1. 备份文件

| 项 | 值 |
| --- | --- |
| 文件 | `deploy/backup/teaclub_pre_migration_2026-11-19T03-46-37.sql` |
| 大小 | 440 KB |
| 表数 | 33（含 flyway_schema_history） |
| 恢复验证 | ✅ **已实际导入临时库验证，24 张表行数与金额完全一致** |

> 备份时出现 `PROCESS privilege` 警告（dump tablespaces），不影响数据完整性，
> 已通过实际恢复验证排除风险。

---

## 2. 表行数基线

| 表 | 行数 | 目标库 |
| --- | ---: | --- |
| `sys_brand` | 1 | iam_db |
| `sys_store` | 4 | iam_db |
| `sys_user` | 6 | iam_db |
| `sys_role` | 5 | iam_db |
| `sys_permission` | 68 | iam_db |
| `sys_user_role` | 6 | iam_db |
| `sys_role_permission` | 179 | iam_db |
| `sys_user_store` | 4 | iam_db |
| `sys_operation_log` | 1200 | iam_db |
| `sys_config` | 0 | iam_db |
| `mem_member` | 3 | member_db |
| `mem_member_level` | 5 | member_db |
| `mem_member_tag` | 0 | member_db |
| `mem_member_tag_rel` | 0 | member_db |
| `prd_category` | 5 | product_db |
| `prd_spu` | 7 | product_db |
| `prd_sku` | 14 | product_db |
| `prd_store_spu` | 24 | product_db |
| `prd_store_sku` | 17 | product_db |
| `prd_inventory` | 0 | inventory_db（预留） |
| `wal_wallet` | 3 | wallet_db |
| `wal_wallet_transaction` | 6 | wallet_db |
| `wal_recharge_plan` | 6 | wallet_db |
| `wal_recharge_plan_store` | 2 | wallet_db |
| `wal_recharge_order` | 2 | wallet_db |
| `oms_order` | 3 | order_db |
| `oms_order_item` | 3 | order_db |
| `oms_order_status_log` | 6 | order_db |
| `pay_payment_order` | 1 | payment_db |
| `pay_notify_log` | 1 | payment_db |
| `pay_refund_order` | 2 | payment_db |

---

## 3. 金额基线（最关键）

```text
wallet_principal   = 255000   钱包实付余额合计
wallet_gift        =  36338   钱包赠送余额合计
txn_principal      = 255000   流水实付变动合计   ← 必须等于 wallet_principal
txn_gift           =  36338   流水赠送变动合计   ← 必须等于 wallet_gift

recharge_paid      =   5000   已支付充值金额
order_paid         =   3412   订单实付合计
order_refunded     =   1750   订单已退合计
payment_success    =   5000   支付成功合计
refund_success     =   3500   退款成功合计
```

### 核心不变量

```text
wallet_principal == txn_principal   (255000 == 255000) ✅
wallet_gift      == txn_gift        ( 36338 ==  36338) ✅
```

---

## 4. 迁移后校验 SQL

迁移到新库后，对每个目标库执行对应片段，结果必须与上表完全一致。

```sql
-- wallet_db
SELECT 'wallet_principal', COALESCE(SUM(principal_balance),0) FROM wal_wallet
UNION ALL SELECT 'wallet_gift',   COALESCE(SUM(gift_balance),0)      FROM wal_wallet
UNION ALL SELECT 'txn_principal', COALESCE(SUM(principal_change),0)  FROM wal_wallet_transaction
UNION ALL SELECT 'txn_gift',      COALESCE(SUM(gift_change),0)       FROM wal_wallet_transaction;

-- 钱包-流水一致性（必须返回 0 行）
SELECT w.id FROM wal_wallet w
LEFT JOIN wal_wallet_transaction t ON t.wallet_id = w.id
GROUP BY w.id, w.principal_balance, w.gift_balance
HAVING w.principal_balance <> COALESCE(SUM(t.principal_change),0)
    OR w.gift_balance      <> COALESCE(SUM(t.gift_change),0);
```

---

## 5. 变更说明

⚠️ **本基线采集于「会员可见口径改为 B 方案」之后**，
因此 `sys_operation_log`（1200 行）、`oms_order`（3 行）、`wal_wallet_transaction`（6 行）
比 `CURRENT-PROJECT-AUDIT.md` 中的初始审计值有所增长（测试过程产生）。

迁移时以**本文件**为准，旧审计文档中的数字作为历史参考。
