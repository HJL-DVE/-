# TeaClub 第五阶段·任务三：member-service 迁移与可见口径 B 验证

| 项目 | 内容 |
| --- | --- |
| 文档编号 | TC-MSA-14 |
| 日期 | 2026-11-19 |
| 范围 | member-service 业务迁移 + order-service 可见性接口 + 可见口径 B 跨服务验证 |
| 状态 | 可见口径 B 实测通过，钱包服务未受影响 |

---

## 1. member-service 迁移清单

| 类型 | 数量 | 内容 |
| --- | --- | --- |
| 实体 | 2 | MemMember, MemMemberLevel |
| DTO | 3 | MemberQuery, MemberUpdateRequest, MemberLevelSaveRequest |
| VO | 2 | MemberVO, MemberLevelVO |
| Mapper | 2 | MemMemberMapper, MemMemberLevelMapper |
| Service | 4 | 接口 2 + 实现 2 |
| Controller | 4 | MemMember, MemMemberLevel, MemberInternal, Health |
| 特有 | 3 | OrderInternalClient, AuthInternalClient, MemberVisibilityResolver |

**钱包数据不迁移到 member-service** —— 会员资料与资金账户分离管理。

---

## 2. 关键改造：可见口径 B 的实现方式变了

### 2.1 单体做法（跨库子查询）

```sql
SELECT DISTINCT m.id
FROM mem_member m
WHERE m.brand_id = #{brandId} AND m.deleted = 0
  AND ( m.register_store_id IN (授权门店)
        OR EXISTS (SELECT 1 FROM oms_order o      -- 跨库！
                   WHERE o.member_id = m.id AND o.deleted = 0
                     AND o.store_id IN (授权门店)) )
```

单体里 `mem_member` 与 `oms_order` 同库，可以直接写子查询。

### 2.2 微服务做法（跨服务调用）

```text
member-service                          order-service
  |                                        |
  |-- Feign: member-ids?brandId&storeIds -->|
  |                                        | 查 oms_order（order_db 独占）
  |<-- [2, 3] 消费过的会员 ID ------------|
  |                                        |
  +-- 本地查 register_store_id IN (授权门店)  |
  |                                        |
  +-- 取并集 = 可见会员集合                 |
```

**可见口径本身完全不变**：注册门店在授权范围内 **或** 在该门店消费过。

### 2.3 新增的 order-service 内部接口

```text
GET /api/order/internal/member-ids?brandId=1&storeIds=1,2
  -> { "code":0, "data":[2,3] }
```

由 `OmsOrderMapper.selectMemberIdsByStoreIds` 实现，只查 order_db。

### 2.4 降级策略（安全优先）

```java
} catch (Exception e) {
    // 关键：不可用时必须失败，不能降级为"全部可见"
    throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE,
            "订单服务暂不可用，无法确定会员可见范围");
}
```

order-service 不可用时**直接失败**而非静默放行 —— 静默放行会导致越权可见。

---

## 3. 可见口径 B 实测结果

### 3.1 数据前提

```text
会员1 张小明 注册门店1（无订单）
会员2 李小雨 注册门店2，但在门店1 有订单   <- 关键样本
会员3 王大力 注册门店1，在门店1 有订单

订单：会员2 在门店1 两单；会员3 在门店1 一单
```

### 3.2 验证结果

```text
账号            授权门店    可见会员数  说明
mgr_xihu        门店1       3          含李小雨（注册门店2，但在门店1消费过）★
mgr_binjiang    门店2       1          仅李小雨（注册门店2）
admin           全部        3          无限制
```

★ 这条正是可见口径 B 的核心：
**李小雨注册在门店2，但她在门店1消费过，因此门店1 的收银台必须能识别她**。
若只看注册门店，会出现"看得到订单却查不到会员"的矛盾。

### 3.3 权限拦截

```text
店长 GET ?registerStoreId=2        -> 10202 没有门店[2]的数据权限
收银员 PUT /api/admin/member/members/1 -> 10201 没有该操作权限
按手机号查（店长）                 -> 200，手机号脱敏 138****0001
```

### 3.4 钱包服务未受影响

```text
GET /api/wallet/internal/balance?brandId=1&memberId=1
  -> 200，实付 50000 / 赠送 8000
```

---

## 4. 本轮修复的问题

| # | 问题 | 根因 | 修复 |
| --- | --- | --- | --- |
| 1 | 跨库子查询 | 单体同库，拆分后 order_db 归 order-service | 改 Feign 调用 + 并集 |
| 2 | `updateStatus` 签名不匹配 | 接口是 (Long,String,String)，实现写成 Map | 统一为三参数 |
| 3 | Controller 传 `reason` | 实体字段是 `freezeReason` | 修正参数名 |
| 4 | `@EnableFeignClients` 重复 | 迁移时重复插入 | 去重 |
| 5 | `common.annotation` 未改包名 | 遗漏 | 改为 `com.teaclub.cloud.web.annotation` |
| 6 | **容器内无 member 类** | **只 compile 没 package，jar 比 class 旧** | **重新 package 后重建镜像** |
| 7 | 品牌/门店接口 500 | `sys_brand` 主键列是 `id` 不是 `brand_id` | 修正 SQL |
| 8 | **分页 total 恒为 0** | **MyBatis-Plus 3.5.9+ 把分页插件拆到 jsqlparser 模块** | **加依赖 + 注册 PaginationInnerInterceptor** |
| 9 | auth 控制器 import 缺失 | 新增 storeNames 接口时漏 import | 补齐 |

> **第 6 项值得记住**：`.dockerignore` 排除了 `target/` 之外的源目录，
> 镜像只 COPY `target/*.jar`。若只跑 `mvn compile` 就 build，
> 镜像里是旧 jar —— 表现为"接口 404 / 类不存在"，很像代码没写对。

---

## 5. 未完成项（如实标注）

| # | 项 | 原因 |
| --- | --- | --- |
| 1 | product-service 迁移 | 未开始 |
| 2 | order-service 完整业务（订单创建/支付/退款） | 只做了可见性查询接口 |
| 3 | payment-service 迁移（含微信支付 v3） | 未开始 |
| 4 | member-service 单元测试 | 本轮只做端到端验证 |
| 5 | 微信身份绑定 | 单体该功能在 auth 侧，member 侧未涉及 |
| 6 | Outbox / Inbox + 事件发布 | 未实现 |
| 7 | 令牌撤销黑名单 | 单体有，微服务未实现 |

> **不能声称完成**：以上 7 项均未实际验证。

---

## 6. 下一批计划

1. **product-service 迁移**（分类/SPU/SKU/门店商品/价格）
2. **order-service 完整业务迁移**
3. **payment-service 迁移**（微信支付 v3 验签/解密，不得 Mock）
4. Outbox / Inbox + 事件发布（MEMBER_REGISTERED / RECHARGE_PAID / WALLET_DEDUCTED 等）
5. 补 member / auth 单元测试
