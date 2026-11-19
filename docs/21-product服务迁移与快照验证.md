# TeaClub 第五阶段·任务三：product-service 迁移与价格快照验证

| 项目 | 内容 |
| --- | --- |
| 文档编号 | TC-MSA-15 |
| 日期 | 2026-11-19 |
| 范围 | product-service 全量迁移（30 文件）+ 门店差异化价格 + 订单快照独立性验证 |
| 状态 | 实测通过 |

---

## 1. 迁移清单（30 个文件）

| 类型 | 数量 | 内容 |
| --- | --- | --- |
| 实体 | 5 | PrdCategory, PrdSpu, PrdSku, PrdStoreSpu, PrdStoreSku |
| DTO | 6 | CategorySaveRequest, SpuSaveRequest, SpuQuery, SkuSaveRequest, StoreShelfRequest, StoreSkuPriceRequest |
| VO | 5 | CategoryVO, SpuVO, SkuVO, StoreSpuVO, StoreSkuVO |
| Mapper | 5 | 含门店差异化价查询 |
| Service | 6 | 接口 3 + 实现 3 |
| Controller | 3 | PrdCategory, PrdSpu, PrdStoreProduct |
| 特有 | 3 | ProductInternalController, AuthInternalClient, MybatisPlusConfig |

---

## 2. 数据校验（迁移后）

```text
prd_spu         7 个
prd_sku        14 个
prd_store_spu  25 个
prd_store_sku  17 个
```

**历史商品 ID 保留**：SPU #1~#6、SKU #1~#8 与迁移前一致，接口返回正常
（招牌珍珠奶茶#1 基准价 1800、经典奶茶#2 1400、芝士葡萄#3 2200）。

---

## 3. 门店差异化价格

### 3.1 实现

```text
门店有效价 = prd_store_sku.store_price（该门店有定价时）
           否则 prd_sku.price（基准价）
```

由 `ProductInternalController.storePrices` 提供，供 order-service 调用。

### 3.2 实测

```text
GET /api/product/internal/store-prices?brandId=1&storeId=1&skuIds=1,2,3
  -> { "1": 1750, "2": 2200, "3": 1400 }

对照基准价：
  SKU1 基准 1800 -> 门店1 价 1750   <- 差异化生效
  SKU2 基准 2200 -> 门店1 价 2200   <- 无差异化，取基准
  SKU3 基准 1400 -> 门店1 价 1400   <- 无差异化，取基准
```

---

## 4. 「订单快照不被商品更新覆盖」实测

**这是本批次最重要的验证。**

```text
订单明细快照单价 = 1750    <- 订单创建时写入 order_db
SKU 1 当前商品价   = 1800   <- 商品后来调价了
-> 快照独立生效 OK
```

即：商品调价只影响新订单，**历史订单金额与商品名称/规格快照不受影响**。
订单明细中的 `sku_name` / `spu_name` / `spec_json` / `unit_price`
均为下单时快照，商品更新不会回写。

---

## 5. 跨库依赖改造

单体的 `PrdStoreProductServiceImpl` 直接注入 `SysStoreMapper`（同库查门店）。
拆分后 iam_db 归 auth-service，改为 **Feign 调用**：

```java
// 门店属 auth-service 域，product-service 不得直接访问 iam_db
if (!storeExists(storeId)) {
    throw new BizException(ResultCode.STORE_NOT_FOUND);
}
```

auth-service 新增 `GET /api/auth/internal/store/{storeId}`。

**降级策略**：auth-service 不可用时按"门店不存在"处理（拒绝操作），不静默放行。

---

## 6. 本轮修复的问题

| # | 问题 | 根因 | 修复 |
| --- | --- | --- | --- |
| 1 | `new Wrappers.<T>lambdaQuery()` 语法错 | 泛型方法不能在 `new` 后写 | 改 `Wrappers.<T>lambdaQuery()` |
| 2 | `SysStoreMapper` 找不到 | 跨库依赖未改造 | 改 Feign 调用 auth-service |
| 3 | `log` 未定义 | 该类无 @Slf4j | 加注解 |
| 4 | product 缺 common-feign/common-redis | 依赖未加 | 补齐 |

---

## 7. 未完成项（如实标注）

| # | 项 | 原因 |
| --- | --- | --- |
| 1 | order-service 完整业务 | 本批次只做 product |
| 2 | payment-service（含微信 Mock 支付） | 未开始 |
| 3 | Outbox / Inbox + 8 类事件 | 未实现 |
| 4 | product-service 单元测试 | 本轮只做端到端 |
| 5 | 微信支付真实接入 | 课程设计，按 Mock 实现 |

> **不能声称完成**：以上 5 项均未实际验证。

---

## 8. 下一批

order-service 全量迁移（订单创建 / 金额计算 / 明细快照 / 状态机 / 取消 / 退款申请），
依赖 product 的门店有效价接口。
