# TeaClub 第五阶段·任务三：payment-service 迁移与支付全链路验证

| 项目 | 内容 |
| --- | --- |
| 文档编号 | TC-MSA-17 |
| 日期 | 2026-11-19 |
| 范围 | payment-service 全量迁移（27 文件）+ Mock 支付 + 支付全链路 |
| 状态 | 订单 → 支付 → 通知 → 订单状态 → 退款 全链路实测通过 |

---

## 1. 迁移清单（27 个文件）

- 实体 3：PayPaymentOrder, PayNotifyLog, PayRefundOrder
- DTO 2 / VO 3 / Mapper 3 / Service 4 / Controller 2
- Gateway 9：PaymentProvider 接口 + Mock/WechatV3 两实现 + 6 DTO
- Config 1：PaymentProperties

### 跨服务依赖改造

单体的 4 个跨域 Mapper/Service 改为 Feign：
payment-service → OrderInternalClient（查订单/标记支付/退款申请）
payment-service → WalletInternalClient（查充值单/退款回冲）
**payment-service 不访问 order_db / wallet_db**。

---

## 2. 微信支付结构（课程设计按 Mock 实现）

PaymentProvider 接口 + MockPaymentProvider（当前启用）+ WechatPayV3Provider（骨架保留）。
业务代码只依赖接口，无 if(isMock) 分支。
所有敏感配置从环境变量读取（WX_MERCHANT_ID / WX_API_V3_KEY 等），.env.docker 不提交。

Mock 支付重要约束：
- 支付单 provider 恒为 MOCK，不伪装成真实微信支付
- 不会因前端 wx.requestPayment 返回值而自动成功
- 支付成功必须由显式 Mock 通知触发，走与真实支付相同的处理链路

---

## 3. 支付全链路实测

1. 创建订单 TC20261119231154000001  UNPAID  应付 1372
2. 创建支付单 PAY20261119231154000001  PAYING  金额 1372  provider=MOCK
3. Mock 支付通知 → 支付成功已处理
4. 订单状态 → PAID（payment → order 跨服务 markPaid）
5. 退款申请 RF20261119231155000002  PROCESSING

### 幂等验证

对同一 paymentNo 连续两次 Mock 通知：第一次/第二次均返回支付成功已处理，
支付单状态 SUCCESS，不重复入账。

### 历史数据保留

历史支付单 PAY20261119114410000103 SUCCESS provider=MOCK 保留。
历史退款单 RF20261119114410000107 SUCCESS 1750 分 保留。

---

## 4. 本轮修复的问题

1. 4 个跨域 Mapper → 2 个 Feign 客户端
2. order 内部接口返回 snake_case → 统一 ord(order, key) Map 读取
3. 批量正则替换破坏括号 → 从干净基线重新生成 + 逐行精确替换 + 括号平衡校验
4. markPaid 不生效（updateById 用 orderNo 而非 id）→ 先查 id 再更新
5. 列表接口测试路径错误 → 正确路径 /api/admin/payment/orders
6. @EnableFeignClients 重复 → 去重
7. Mock/Wechat Provider 缺 @Slf4j → 补注解
8. wallet 缺充值单查询 → 新增 getRechargeOrder

---

## 5. 未完成项（如实标注）

1. 真实微信支付 API v3 验签/解密（课程设计按 Mock，WechatV3 保留骨架）
2. Outbox / Inbox + 8 类事件发布
3. 支付超时关单定时任务
4. payment-service 单元测试（本轮只做端到端）
5. 退款通知端到端闭环

> 不能声称完成：以上 5 项均未实际验证。

---

## 6. 下一批

Outbox / Inbox + 8 类事件发布，用本地事务 + Outbox + Saga 补偿替代直接跨库修改。
