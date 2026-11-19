# ============================================================
# 数据迁移映射表
#
# 源：单体库 teaclub（本机 MySQL 3306）
# 目标：各微服务独立库（容器 MySQL 3307）
#
# 原则：
#   1. 保留历史 ID —— 目标表主键与源表一致，不重新生成
#   2. 不触发业务事件 —— 迁移只搬数据，不发消息、不改业务状态
#   3. 资金表逐字段核对，余额与流水必须同时迁移
#   4. 每个服务只搬属于自己的表
# ============================================================

## auth-service -> iam_db

| 源表 | 目标表 | 目标服务 | 迁移方式 | 关键校验 |
| --- | --- | --- | --- | --- |
| `sys_brand` | `sys_brand` | auth-service | 全量复制 | 扣款顺序字段 wallet_deduct_order 保留 |
| `sys_store` | `sys_store` | auth-service | 全量复制 | 门店数 = 4 |
| `sys_user` | `sys_user` | auth-service | 全量复制 | 密码 BCrypt 原样保留 |
| `sys_role` | `sys_role` | auth-service | 全量复制 | 角色数 |
| `sys_permission` | `sys_permission` | auth-service | 全量复制 | 权限点 68 |
| `sys_user_role` | `sys_user_role` | auth-service | 全量复制 | 关联数 |
| `sys_role_permission` | `sys_role_permission` | auth-service | 全量复制 | 关联数 |
| `sys_user_store` | `sys_user_store` | auth-service | 全量复制 | **数据权限核心**，行数必须一致 |
| `sys_config` | `sys_config` | auth-service | 全量复制 | 空表也建 |
| `sys_operation_log` | `sys_operation_log` | auth-service | 全量复制 | 770 行，最大表 |

## member-service -> member_db

| 源表 | 目标表 | 目标服务 | 迁移方式 | 关键校验 |
| --- | --- | --- | --- | --- |
| `mem_member` | `mem_member` | member-service | 全量复制 | **保留历史会员 ID**，不得重复创建 |
| `mem_member_level` | `mem_member_level` | member-service | 全量复制 | 等级 5 |
| `mem_member_tag` | `mem_member_tag` | member-service | 全量复制 | 标签 |
| `mem_member_tag_rel` | `mem_member_tag_rel` | member-service | 全量复制 | 关联 |

> **钱包数据不迁移到 member-service**。会员资料与资金账户分离管理。

## product-service -> product_db

| 源表 | 目标表 | 目标服务 | 迁移方式 | 关键校验 |
| --- | --- | --- | --- | --- |
| `prd_category` | `prd_category` | product-service | 全量复制 | 分类 5 |
| `prd_spu` | `prd_spu` | product-service | 全量复制 | **保留历史商品 ID** |
| `prd_sku` | `prd_sku` | product-service | 全量复制 | **保留已有 SKU**，含价格 |
| `prd_store_spu` | `prd_store_spu` | product-service | 全量复制 | 门店商品关联 |
| `prd_store_sku` | `prd_store_sku` | product-service | 全量复制 | **门店差异化价格** |
| `prd_inventory` | `prd_inventory` | product-service | 全量复制 | 库存（预留） |

> 订单中的商品价格快照在 order_db，不受商品更新影响。

## wallet-service -> wallet_db

| 源表 | 目标表 | 目标服务 | 迁移方式 | 关键校验 |
| --- | --- | --- | --- | --- |
| `wal_wallet` | `wal_wallet` | wallet-service | 全量复制 | **保留原钱包 ID**、**保留每会员原余额** |
| `wal_wallet_transaction` | `wal_wallet_transaction` | wallet-service | 全量复制 | **保留全部流水**、含变动前后余额与来源 |
| `wal_recharge_order` | `wal_recharge_order` | wallet-service | 全量复制 | **保留原充值记录**，状态原样 |
| `wal_recharge_plan` | `wal_recharge_plan` | wallet-service | 全量复制 | 充值套餐 |
| `wal_recharge_plan_store` | `wal_recharge_plan_store` | wallet-service | 全量复制 | 套餐适用门店 |

### 钱包专项校验（不变量）

```text
I1  每个会员余额 >= 0
I2  wallet.principal_balance = SUM(txn.principal_change)
I3  wallet.gift_balance      = SUM(txn.gift_change)
I4  txn.after = txn.before + txn.change
I5  同一 (biz_type, biz_no) 只有一条流水（uk_txn_biz）
I6  流水链式衔接：同一钱包按时间排序，after == 上一条的 before
```

### 冻结方案（不通过改余额模拟）

源表 `wal_wallet` 已有 `status` 字段（NORMAL/FROZEN），
**直接迁移该字段**，不新增列、不改余额。

若后续需要部分金额冻结（如冻结 10 元但余额 100 元），
应新增独立的 `wal_wallet_freeze` 冻结记录表（可用余额 = 余额 - 冻结额），
不得直接修改 `principal_balance` —— 那会破坏"余额 = 流水累计"不变量。

## order-service -> order_db

| 源表 | 目标表 | 目标服务 | 迁移方式 | 关键校验 |
| --- | --- | --- | --- | --- |
| `oms_order` | `oms_order` | order-service | 全量复制 | **保留原订单号**、会员与门店关系、历史支付状态 |
| `oms_order_item` | `oms_order_item` | order-service | 全量复制 | **保留商品名称与规格快照**、历史订单金额 |
| `oms_order_status_log` | `oms_order_status_log` | order-service | 全量复制 | 状态流转记录 |

## payment-service -> payment_db

| 源表 | 目标表 | 目标服务 | 迁移方式 | 关键校验 |
| --- | --- | --- | --- | --- |
| `pay_payment_order` | `pay_payment_order` | payment-service | 全量复制 | **保留原商户订单号**、微信交易号 |
| `pay_notify_log` | `pay_notify_log` | payment-service | 全量复制 | 回调幂等记录 |
| `pay_refund_order` | `pay_refund_order` | payment-service | 全量复制 | 退款记录与状态 |

## 保留在单体（不迁移）

| 源表 | 原因 |
| --- | --- |
| `sys_idempotency_record` | 幂等表语义随各服务的 uk_txn_biz 分散到各库 |
| `flyway_schema_history` | 各服务独立 Flyway 版本 |

## 迁移阶段禁止事项

```text
禁止 1: 触发历史充值入账（不得调用 rechargeIn 回灌）
禁止 2: 触发历史支付成功（不得调用支付回调接口）
禁止 3: 触发历史退款消息（不得发 RabbitMQ 事件）
禁止 4: 修改余额来"修正"数据（不变量必须成立，否则修数据而不是修余额）
```

历史数据迁移由本脚本直接搬数据完成，
新业务事件处理由各服务的 Outbox/Inbox 负责，两条路径严格分离。
