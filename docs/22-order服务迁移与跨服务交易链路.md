# TeaClub 第五阶段·任务三：order-service 迁移与跨服务交易链路验证

| 项目 | 内容 |
| --- | --- |
| 文档编号 | TC-MSA-16 |
| 日期 | 2026-11-19 |
| 范围 | order-service 全量迁移（19 文件）+ 跨服务交易链路（product/member/wallet/auth） |
| 状态 | **下单 → 余额支付 → 状态流转 全链路实测通过** |

---

## 1. 迁移清单（19 个文件）

| 类型 | 数量 | 内容 |
| --- | --- | --- |
| 实体 | 3 | OmsOrder, OmsOrderItem, OmsOrderStatusLog |
| DTO | 5 | OrderCreateRequest, OrderItemRequest, OrderQuery, OrderQuoteRequest, OrderStatusUpdateRequest |
| VO | 3 | OrderVO, OrderItemVO, OrderQuoteVO |
| Mapper | 3 | 含 `selectByNoForUpdate` 行锁、`selectMemberIdsByStoreIds` 可见性查询 |
| Service | 3 | OrderService, OrderAmountCalculator, OrderServiceImpl |
| Controller | 2 | OrderController, OrderInternalController |
| 枚举 | 1 | OrderStatus（状态机） |
| 特有 | 4 | WalletInternalClient, MemberInternalClient, ProductInternalClient, AuthInternalClient |

---

## 2. 跨服务依赖改造（4 处）

单体的 `OrderServiceImpl` / `OrderAmountCalculator` 直接注入了 6 个跨域 Mapper
（product 4 个 + member 2 个）。拆分后各表归属独立库，改为 **Feign 调用**：

```text
order-service
  ├─ ProductInternalClient  → product-service : 取门店有效价 + 商品快照
  ├─ MemberInternalClient   → member-service  : 取会员状态 + 等级折扣率 + 更新消费档案
  ├─ WalletInternalClient   → wallet-service  : 余额扣款 / 退款回冲
  └─ AuthInternalClient     → auth-service    : 门店存在性与名称
```

**order-service 不访问 product_db / member_db / wallet_db / iam_db**。

### 关键约束

| 操作 | 重试 | 幂等 |
| --- | --- | --- |
| 余额扣款 | **不启用自动重试** | 幂等键 = 订单号，wallet 侧 `uk_txn_biz` 唯一索引兜底 |
| 订单创建 | 不重试 | 订单号唯一 |
| 商品查询 | 可重试（只读） | 无副作用 |

---

## 3. 端到端实测（真实交易）

### 3.1 报价（跨服务取价 + 等级折扣）

```text
POST /api/admin/order/quote  { storeId:1, memberId:1, items:[{skuId:1, quantity:2}] }

-> 总价       = 3500      （门店价 1750 × 2）
   等级折扣   = 70        （银卡 9.8 折：3500 × 200/10000 = 70）
   应付       = 3430
   会员等级   = 银卡会员  折扣率 9800
   行明细     = 招牌珍珠奶茶/中杯  单价 1750  数量 2  行实付 3430
```

### 3.2 创建订单（快照写入）

```text
POST /api/admin/order/orders
-> 订单号 = TC20261119222754000001
   状态   = UNPAID
   应付   = 3430
   快照   = { skuName:"招牌珍珠奶茶/中杯",
              spuName:"招牌珍珠奶茶",
              specJson:"{"杯型": "中杯", "温度": "正常冰"}",
              unitPrice:1750 }
```

**商品名与规格 JSON 快照完整写入 order_db**，之后商品调价不影响历史订单。

### 3.3 余额支付（order → wallet 跨服务扣款）

```text
POST /api/admin/order/orders/{orderNo}/pay-balance
-> code = 0，订单状态 = PAID，实付 = 3430
```

### 3.4 钱包流水核对（GIFT_FIRST 扣款顺序正确）

```text
id  订单号                    实付变动   赠送变动   扣款前(实/赠)   扣款后(实/赠)
168 TC-TEST-...                0          -3500     50000/8000      50000/4500
169 TC20261119222754000001      0          -3430     50000/4500      50000/1070
170 TC20261119222836000002      -645       -1070     50000/1070      49355/0
```

**第 170 笔是关键证据**：应付 1715 = 赠送金 1070（优先扣完）+ 实付 645。
证明 GIFT_FIRST 顺序在跨服务调用后依然正确。

**不变量复核**：`principal_balance = SUM(principal_change)` → `ok=1` ✅

---

## 4. 幂等与状态机

```text
重复余额支付同一订单 -> code=0（幂等命中，未重复扣款）
取消已支付订单       -> 10002 "仅未支付订单可取消；已支付订单请走退款流程"
```

**状态机规则**（来自单体 `OrderStatus`）：

```text
UNPAID    -> PAID | CANCELLED
PAID      -> COMPLETED | REFUNDING
COMPLETED -> REFUNDING
REFUNDING -> REFUNDED | PAID
CANCELLED -> （终态）
REFUNDED  -> （终态）
```

非法流转一律拒绝。

---

## 5. API 兼容性

单体与微服务版 **8 个接口完全一致**：

```text
POST   /api/admin/order/quote
POST   /api/admin/order/orders
POST   /api/admin/order/orders/{orderNo}/pay-balance
GET    /api/admin/order/orders
GET    /api/admin/order/orders/{orderNo}
POST   /api/admin/order/orders/{orderNo}/cancel
PATCH  /api/admin/order/orders/{orderNo}/status
```

前端无需改动。

---

## 6. 本轮修复的问题

| # | 问题 | 根因 | 修复 |
| --- | --- | --- | --- |
| 1 | 6 个跨域 Mapper 无法编译 | 跨库依赖未改造 | 改为 4 个 Feign 客户端 |
| 2 | `OmsOrderMapper` 丢失可见性方法 | 批量迁移覆盖了手写文件 | 合并手写 + 迁移版本 |
| 3 | `MemberInternalClient.levelInfo` 未定义 | 客户端替换未生效 | 重写客户端文件 |
| 4 | `selectByNo` 缺失 | Mapper 被覆盖 | 补回普通查询方法 |
| 5 | **等级折扣恒为 0** | member 返回 `level_id`，代码读 `levelId` | 统一 snake_case |
| 6 | member 内部接口返回假数据 | 原是演示实现 | 改为查 mem_member 真实数据 |
| 7 | `quote` 方法缺 `current` 变量 | 改造时作用域错误 | 补 `LoginUser current` |
| 8 | `currentBrandId()` 依赖 auth 查询 | 冗余且可能失败 | 改用 `current.getBrandId()` |
| 9 | 余额支付 500 | 测试用错路径（pay-by-balance） | 实际为 `pay-balance` |

> **第 5、6 项是隐蔽 bug**：接口通了、返回 200，但**字段名对不上导致折扣恒为 0**。
> 单看"接口通了"会误判为完成 —— 必须核对**业务数值**是否符合预期。

---

## 7. 未完成项（如实标注）

| # | 项 | 原因 |
| --- | --- | --- |
| 1 | payment-service（含微信 Mock 支付） | 未开始 |
| 2 | 退款申请 / 退款流程闭环 | 状态机有 REFUNDING 流转，接口未接 payment |
| 3 | 优惠券抵扣（couponDiscount） | 营销模块未开发，恒传 0 |
| 4 | Outbox / Inbox + 8 类事件 | 未实现 |
| 5 | order-service 单元测试 | 本轮只做端到端 |
| 6 | 微信支付真实接入 | 课程设计，按 Mock 实现 |

> **不能声称完成**：以上 6 项均未实际验证。

---

## 8. 下一批

payment-service 迁移（微信 Mock 支付，保留可切换到真实 API v3 的结构）：
支付订单 / 预支付 / 回调幂等 / 退款 / 通知 / 状态机。
之后做 Outbox / Inbox 把 order-payment-wallet 串成一致闭环。
