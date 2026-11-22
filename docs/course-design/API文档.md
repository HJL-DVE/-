# TeaClub API 接口文档

> 由后端 Controller 实际代码扫描生成（SpringDoc Swagger 亦可用 /v3/api-docs 查看）。
> 所有接口统一响应包装：`{code, message, data, traceId, timestamp}`，code=0 成功。
> 鉴权：管理端 Bearer JWT（权限点校验）；会员端 Bearer JWT；内部接口 X-Internal-Token（网关不放行）。

---

## 一、auth-service（:9001）用户/角色/权限/门店/品牌
## 二、member-service（:9002）会员与等级

| 方法 路径 | 说明 |
| --- | --- |
| GET /api/member/profile | 当前会员资料 |
| PUT /api/member/profile | 修改资料 |
| GET /api/member/levels | 等级列表 |
| GET /api/admin/member/members | 会员分页（member:list） |
| PUT /api/admin/member/levels/{id} | 编辑等级（member:level:update） |
| GET /api/admin/marketing/level-benefits | 等级营销权益 |

## 三、product-service（:9003）商品

| 方法 路径 | 说明 |
| --- | --- |
| GET /api/product/stores/{storeId}/spus | 门店商品列表 |
| GET /api/product/stores/{id}/categories | 分类 |
| GET /api/admin/product/spus | SPU 分页（product:spu:list） |
| POST /api/admin/product/categories | 新建分类 |

## 四、wallet-service（:9004）钱包与充值

| 方法 路径 | 说明 |
| --- | --- |
| GET /api/wallet/account | 钱包余额（本金/赠送） |
| GET /api/wallet/transactions | 流水分页 |
| GET /api/wallet/plans | 充值套餐 |
| POST /api/wallet/recharges | 创建充值单 |
| GET /api/admin/wallet/plans | 套餐管理（wallet:plan:list） |
| GET /api/admin/wallet/stats | 钱包统计 |

## 五、order-service（:9005）订单

| 方法 路径 | 说明 |
| --- | --- |
| POST /api/order/quote | 试算（支持 memberCouponId） |
| POST /api/order/orders | 创建订单（memberCouponId 可选） |
| POST /api/order/orders/{no}/pay-wallet | 钱包支付 |
| POST /api/order/orders/{no}/cancel | 取消 |
| GET /api/order/orders | 订单列表 |
| GET /api/admin/order/stats/summary | 销售统计（order:statistics） |

## 六、payment-service（:9006）支付

| 方法 路径 | 说明 |
| --- | --- |
| POST /api/payment/create | 创建支付单（PAYING） |
| GET /api/payment/orders/{no} | 支付单查询 |
| POST /api/payment/mock/notify | Mock 支付成功通知（白名单） |
| POST /api/payment/mock/fail | Mock 支付失败（白名单） |
| POST /api/payment/refunds | 创建退款单 |

## 七、marketing-service（:9007）营销

| 方法 路径 | 说明 |
| --- | --- |
| GET /api/marketing/coupons/available | 券中心（可领模板） |
| POST /api/marketing/coupons/receive | 领券 |
| GET /api/marketing/coupons/mine / usable | 我的券 / 可用券 |
| GET /api/marketing/points/account / transactions | 积分账户 / 流水 |
| POST /api/marketing/points/sign-in | 签到 |
| GET/POST /api/marketing/points-goods[/exchange] | 积分商城 |
| GET/POST /api/marketing/activities[/join] | 活动 |
| GET /api/marketing/level-benefits/level/{id} | 等级权益 |
| POST /api/admin/marketing/coupons/templates | 券模板 CRUD |
| GET/POST /api/admin/marketing/activities | 活动 CRUD（admin） |
| POST /api/admin/marketing/level-benefits | 权益维护 |

## 八、report-service（:9008）报表

| 方法 路径 | 说明 |
| --- | --- |
| GET /api/report/dashboard/overview | 总部驾驶舱 |
| GET /api/report/dashboard/trend?days= | 趋势 |
| GET /api/report/store/overview / ranking / compare | 门店 |
| GET /api/report/product/ranking / trend | 商品 |
| GET /api/report/member/overview / growth | 会员 |
| GET /api/report/recharge / payment / refund / marketing /overview | 统计 |
