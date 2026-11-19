## [第六阶段] Web 管理后台 + 微信小程序 + 会员端接口补齐（2026-11-21）

### 目录调整（按需求方意见）

前端从 `backend/` 迁出，收敛到与 `backend/` 平级的 `frontend/`：
`frontend/teaclub-admin`（Vue 3 + TS + Element Plus）、`frontend/teaclub-miniprogram`（uni-app + Vue 3 + TS）。
README / docs/TASKS / docs/06 已同步，并说明与原规划（`web/`+`miniapp/`）的差异。

### 关键前提纠正（如实记录）

任务书假设「后端已迁移完，不需要再开发后端」，但实际核查：
- 钱包管理端（`/api/admin/wallet/**`）**迁移时漏搬**，单体有 3 个 controller；
- **会员端（C 端）接口一个都不存在**，单体时代也只有管理端；
- 无仪表盘统计、无会员码查询、无微信登录、无 `mem_member_wx` 表。

为满足「禁止静态 JSON 代替真实数据」，按**薄封装**原则补齐：新 controller 只做身份识别、
出入参适配、归属校验，业务全部复用已迁移的 service，未重写任何资金/订单逻辑。

### 新增能力

| 模块 | 内容 |
| --- | --- |
| 网关双令牌 | `JwtAuthGlobalFilter` 先解管理端再解会员端，会员令牌下发 `X-Member-Id`；白名单补 6 条会员端匿名路径 + `/api/payment/mock/**` |
| 会员身份上下文 | `InternalHeaders.MEMBER_ID`、`LoginUser.memberId`、`UserContext.requireMemberId()`、`JwtProvider.issueMemberToken()` |
| 钱包管理端 | 补齐 `WalletAdminController` / `RechargeAdminController` / `RechargePlanAdminController` + 查询 service/VO（16 个端点） |
| 会员端 | auth（wx-login/register/login/me/stores）、member（profile/levels）、product（分类/列表/详情/热门）、order（quote/orders/pay-wallet）、payment（create/query）、wallet（account/transactions/plans/recharges） |
| 统计 | `order/stats/summary`、`member/members/stats`、`wallet/stats` |
| 会员码 | `GET /api/admin/member/members/by-code`（收银台扫码） |
| 前端 | Web 19 个视图（13 契约页 + 收银台 + 钱包账户 + 通知日志 + 错误页 + 占位页）、小程序 19 个页面 |

### 修复的真实缺陷（6 个，全部由真联调暴露）

1. **会员令牌过不了网关** —— 只 `parseAdminToken` → 双令牌解析
2. **匿名浏览被拦** —— 白名单缺会员端路径 → 补 6 条
3. **`NumberFormatException: For input string: "null"`** —— Feign 拦截器 `String.valueOf(null)` 透传字面量 `"null"`，下游 `Long.valueOf("null")` 崩 → 两端都归一化
4. **支付记录/退款/通知日志 `total=0` 且一次返回全表** —— payment-service 缺 `PaginationInnerInterceptor` → 补配置 + `mybatis-plus-jsqlparser` 依赖
5. **会员查不到充值套餐（10404）** —— wallet 的 Feign 客户端指向 member-service 不存在的 5 个内部接口 → 新增 `MemberBriefInternalController`
6. **`MemberNoAllocator` 调不存在的方法** —— 并发协作时 mapper 方法丢失 → 补 `selectMaxMemberNo`

### 实测

| 脚本 | 结果 |
| --- | --- |
| `17-phase6-smoke.mjs` | **PASS=28 FAIL=0** |
| `18-recharge-pay-closed-loop.mjs` | **PASS=17 FAIL=0**（充值 100/10 入账、重复通知幂等、下单 17.50、钱包扣款分毫不差） |
| `19-refund-via-gateway.mjs` | **PASS=14 FAIL=0**（重复退款被拒、退款回调、订单 REFUNDED） |
| `12-fund-invariants.mjs` | 断裂数=0；`wallet/reconcile` → `consistent=true, inconsistentCount=0` |

新增文档：docs/33 / 34 / 35 / 36；新增脚本：17 / 18 / 19。

### 未完成（如实）

真实微信登录与支付仍为 Mock（无商户资质，既定策略）；小程序未在微信开发者工具内跑运行时；
8 条菜单为占位页；订单支付走 JSAPI 无 `codeUrl`（Native 需显式指定 channel）；无自动化测试；admin 主包 1.29 MB 未按需拆分。

---

## [第五阶段·任务四补齐] ORDER_PAID 真实消费者 + 会员档案事件驱动（2026-11-20）

把会员消费档案从「订单服务同步 Feign 调用」改成「订阅 `ORDER_PAID` 事件异步累加」，
这是 8 类事件中**第一个真正有消费者的**。

### 新增

- `OrderPaidConsumer`（member-service）：订阅 `teaclub.member.order-paid.queue`，DLQ 兜底
- `MemberConsumeStatsUpdater`：Inbox 认领与 `UPDATE mem_member` **同一本地事务**
- `MqConstants.RK_ORDER_PAID` / `Q_MEMBER_ORDER_PAID`

### 删除

- `OrderServiceImpl.updateMemberStats` 及其两处调用（余额支付、支付成功回写）
- `MemberInternalClient.updateConsumeStats`

改后订单支付链路不再依赖 member-service 可用性；`/api/member/internal/consume-stats` 保留但标记 `@Deprecated`。

### 顺带修掉的两个真实缺陷

1. **Outbox 事件双重序列化**：Relay 发 `String` 导致 Jackson 二次编码，消费端报
   `Cannot construct instance of EventMessage (no String-argument constructor)`
   -> Relay 改发 `JsonNode`，基类解包 textual 节点
2. **消息转换器信任包过窄**：`ObjectNode is not in the trusted packages: [java.util, java.lang]`
   -> `DefaultJackson2JavaTypeMapper` + `trustedPackages("*")` + `TypePrecedence.INFERRED`

### 实测

| 脚本 | 结果 |
| --- | --- |
| 15-order-paid-consumer | 14/14 通过，累加 71430→73145，端到端时延约 1 秒 |
| 16-consumer-inbox-2nd-layer | 5/5 通过，删掉 Redis 幂等键后 DB Inbox 仍拦住重复累加 |

重复消息是真的投进了 Broker（`routed=true`），拦住它的是消费者与 `sys_inbox_message` 唯一键。

新增文档：docs/32；新增测试脚本：15 / 16。

---

## [第五阶段·任务四补齐] 超时关单 / Outbox 接入 / 跨服务对账 / 部分冻结 / 单元测试（2026-11-20）

| # | 原缺口 | 现状 | 实测 |
| --- | --- | --- | --- |
| 1 | 支付超时关单 | PaymentTimeoutCloser + CLOSED 状态 | PAYING → CLOSED，订单保持 UNPAID |
| 2 | Outbox 未接业务 | 三服务业务方法内发 8 类事件 | order/wallet/payment 均 SENT |
| 3 | 无跨服务对账 | OrderWalletReconciliationService | checked=7 issues=0 |
| 4 | 冻结不支持部分确认 | confirmFreezePartial | 扣 1200/300，释放剩余 1800/700 |
| 5 | 无单元测试 | 3 个测试类 | 15 用例全部通过 |

新增文档：docs/31；新增测试脚本：13 / 14。

---
## [第五阶段·任务四补充] 支付通知身份校验落地（2026-11-20）

项目为课程设计、不会上线，因此不申请微信商户资质。
但「通知身份校验」逻辑不再挂骨架，而是真实实现并可测：

### 新增

- `MockNotifySignatureVerifier`：AppID 比对 / 商户号比对 / 签名校验三条规则
- 配置项：`teaclub.payment.mock.expected-app-id|expected-mch-id|sign-secret`
- 严格模式开关：`enforce-notify-check`（缺字段即拒）
- 签名算法：`SHA-256(appId|mchId|paymentNo|amount|secret)`，明确标注为同构占位

### 顺带修正

`application-docker.yml` 中 `teaclub.payment` 配置段此前从未真正写入，
一直依赖 Java 默认值。现已补全 provider / wechat / mock 三段。

### 实测（9/9 断言通过）

| 场景 | 结果 |
| --- | --- |
| AppID 不匹配 | 拒绝，支付单保持 PAYING |
| 商户号不匹配 | 拒绝，支付单保持 PAYING |
| 签名校验失败 | 拒绝，支付单保持 PAYING |
| 正确签名 | 通过，支付单转 SUCCESS |
| 未携带校验字段 | 宽松模式兼容，正常处理 |

支付测试报告由 9/11 更新为 **11/11 全部通过**。

---
## [第五阶段·任务四] 分布式事务与资金一致性专项测试（2026-11-20）

### 新增能力

- **钱包冻结**：`wal_wallet_freeze` 表 + freeze/confirm/release，冻结不动账户余额
- **Saga 状态机**：`oms_payment_saga` + REQUIRES_NEW 独立事务，消除孤儿扣款
- **补偿任务**：PaymentSagaCompensator（30s）/ FreezeExpiryScheduler（10s）
- **定期对账**：WalletReconciliationService，校验 I1–I6 不变量（10min）
- **充值全链路**：补齐充值单创建 + 支付成功入账（原 RECHARGE 分支为空）

### 发现并修复的真实缺陷

1. **重复退款漏洞**：可退金额读 order.refunded_amount（异步才更新）
   -> 改为 payment_db 本地 `SUM(pay_refund_order)` 校验，同事务天然幂等
2. **充值支付不入账**：payment 的 RECHARGE 分支是空的
   -> 补 wallet.processRechargePaid + 充值单状态推进
3. **充值单无法创建**：`pay_channel` NOT NULL 无默认值
   -> V4 迁移改为可空

### 实测结果

| 类别 | 结果 |
| --- | --- |
| 资金十项检查 | 10/10 通过 |
| 并发（20并发/30幂等/20超余额） | 全通过，无超扣无负余额 |
| 充值一致性（100元+20赠，重复5次通知） | 11/11 通过，只入账一次 |
| 订单-钱包10场景 + 真实宕机恢复 | 12/12 断言通过 |
| 支付11场景 | 9 通过，3 项属真实微信范畴（骨架） |
| 退款（微信Mock+钱包） | 全通过，重复退款已拦截 |
| Saga 补偿 | 滞留记录 30 秒内自动回冲，余额 +1715 |
| 对账 | passed=true, 8 钱包 / 45 流水 / 0 不一致 |

### 关键不变量

```text
钱包实付合计 292475 = 流水实付累计 292475
钱包赠送合计  40788 = 流水赠送累计  40788
余额与流水不一致的钱包数 = 0
```

### 文档

- docs/25-资金业务测试报告.md
- docs/26-并发测试报告.md
- docs/27-支付测试报告.md
- docs/28-退款测试报告.md
- docs/29-分布式事务设计文档.md
- docs/30-异常恢复流程图.md

---
## [第五阶段·任务三七批] 分布式事件 Outbox / Inbox（2026-11-19）

- 8 类领域事件常量（含 event_id/业务单号/版本/产生时间/trace_id）
- Transactional Outbox：业务事务内登记事件 + Relay 定时投递
- Consumer Inbox：数据库主键幂等 + Redis 守卫双层去重
- 实测：事件登记 ORDER_PAID → Relay 投递 → SENT=1

---
## [决策变更] 会员可见口径 U-09 → B 方案

### 背景

原实现（A 方案）中，门店角色只能看到"注册门店在授权范围内"的会员。
实测发现业务矛盾：会员在 A 店注册、到 B 店消费时，B 店能看到订单却查不到会员资料，
收银台无法识别会员身份、无法核验权益。

### 决策

采用 **B 方案**：可见 = 注册门店在授权范围 **或** 在该门店消费过。

### 实现

- `MemMemberMapper.selectVisibleMemberIds` —— 新增统一可见性查询（注册门店 IN 授权 OR EXISTS 本店订单）
- `MemMemberServiceImpl` —— 列表 / 手机号查询 / 详情统一走 B 口径
- `WalletQueryServiceImpl` —— 钱包查询同步改为 B 口径，避免"能看会员查不到钱包"
- 越权参数 `registerStoreId` 改为**显式拒绝**而非静默忽略

### 验证

`scripts/member-visibility-test.mjs`：12 项断言全通过
- 跨店消费过的会员可见 ✅
- 未跨店消费的会员仍不可见（隔离有效）✅
- 越权参数被显式拒绝 ✅
- 写操作权限不受影响 ✅

全量回归：44 单元 + 99 + 19 + 59 + 21 + 21 = 263 项，0 失败。

---

## [第五阶段·任务三第六批] payment-service 迁移与支付全链路（2026-11-19）

### 迁移 27 个文件
实体 3 / DTO 2 / VO 3 / Mapper 3 / Service 4 / Controller 2 / Gateway 9 / Config 1

### 微信支付结构（课程设计按 Mock）
PaymentProvider 接口 + MockPaymentProvider（当前启用）+ WechatPayV3Provider（骨架保留）。
业务代码只依赖接口，敏感配置从环境变量读取。

### 全链路实测
订单 UNPAID → 支付单 PAYING(provider=MOCK) → Mock 通知 → 订单 PAID → 退款 PROCESSING。
幂等：重复支付通知不重复入账；历史支付/退款记录保留。

---
## [第五阶段·任务三第五批] order-service 迁移与跨服务交易链路（2026-11-19）

### 迁移 19 个文件

实体 3 / DTO 5 / VO 3 / Mapper 3 / Service 3 / Controller 2 / OrderStatus 1 / 4 个 Feign 客户端

### 跨服务依赖改造

单体的 6 个跨域 Mapper 改为 4 个 Feign 客户端：
product（取价+快照）、member（等级折扣+消费档案）、wallet（扣款）、auth（门店）。

### 端到端实测

- 报价：总价 3500 -> **银卡 9.8 折减 70** -> 应付 3430
- 创建订单：状态 UNPAID，**快照含商品名与规格 JSON**
- 余额支付：order -> wallet 跨服务 -> 状态 **PAID**
- 钱包流水核对：**GIFT_FIRST 顺序正确**（赠送 1070 优先扣 + 实付 645 = 1715）
- 不变量：余额 = 流水累计 -> ok=1
- 幂等：重复支付不重复扣款
- 状态机：已支付订单取消被拒（10002）
- API：8 个接口与单体完全一致

### 隐蔽 bug 记录

member 内部接口返回 `level_id` 而 order 读 `levelId` -> **折扣恒为 0 但接口返回 200**。
必须核对业务数值，不能只看接口通不通。

---

## [第五阶段·任务三第四批] product-service 迁移与价格快照验证（2026-11-19）

### 迁移 30 个文件

实体 5 / DTO 6 / VO 5 / Mapper 5 / Service 6 / Controller 3 / ProductInternalController / AuthInternalClient

### 实测

- 数据量：SPU 7 / SKU 14 / 门店SPU 25 / 门店SKU 17，**历史 ID 保留**
- 门店差异化价：SKU1 基准 1800 -> 门店1 价 **1750**（差异化生效）
- **订单快照独立性**：订单快照单价 1750 vs SKU1 当前价 1800 -> **快照独立生效**
- 跨库依赖：`SysStoreMapper` -> Feign 调 auth-service（降级为拒绝操作）

---

## [第五阶段·任务三第三批] member-service 迁移与可见口径 B 验证（2026-11-19）

### 关键改造：可见口径 B 从跨库子查询改为跨服务调用

单体用一个 SQL 的 `EXISTS` 子查询同时查 `mem_member` 与 `oms_order`（同库）。
拆分后 order_db 归 order-service，改为：

```text
member-service --Feign--> order-service: /api/order/internal/member-ids
             本地查 register_store_id IN (授权门店)
             两者取并集 = 可见会员集合
```

可见口径本身不变：注册门店在授权范围内 **或** 在该门店消费过。

order-service 不可用时**直接失败**而非静默放行（静默放行 = 越权可见）。

### 实测结果

| 账号 | 授权门店 | 可见会员 |
| --- | --- | --- |
| mgr_xihu | 门店1 | **3 个**（含李小雨：注册门店2 但在门店1消费过） |
| mgr_binjiang | 门店2 | **1 个** |
| admin | 全部 | **3 个** |

拦截：店长传 registerStoreId=2 → 10202；收银员改资料 → 10201；手机号脱敏 138****0001。
钱包服务未受影响（余额 50000/8000 正常）。

### 本轮修复 9 个问题

- **容器内无 member 类**：只 compile 没 package，镜像 COPY 的是旧 jar
- **分页 total 恒为 0**：MyBatis-Plus 3.5.9+ 把分页插件拆到 jsqlparser 模块
- 跨库子查询改 Feign
- updateStatus 签名、freezeReason 参数名、@EnableFeignClients 重复、common.annotation 包名、brand_id 列名等

---

## [第五阶段·任务三第二批] auth-service 业务迁移 + 目录结构调整（2026-11-19）

### 目录结构调整

以 `backend/` 为微服务父目录：

```text
backend/
├── src/  pom.xml            现有单体（保留，未改动）
├── teaclub-cloud/           微服务工程（16 模块）
├── teaclub-admin/           管理后台前端
├── teaclub-miniprogram/     小程序前端
└── infrastructure/
    ├── docker/              Compose 编排
    └── migration/           数据迁移 + 备份
```

同步修正 compose build context、迁移脚本 ROOT 常量、.gitignore 路径。

### auth-service 业务迁移（58 个文件）

实体 9 / DTO 13 / VO 9 / Mapper 9 / Service 12 / Controller 6 / TokenPrincipalResolver 1

### 数据权限端到端实测

| 账号 | 角色 | 权限点 | 可见门店 |
| --- | --- | --- | --- |
| admin | SUPER_ADMIN | 68 | 全部 3 家 |
| mgr_xihu | STORE_MANAGER | 28 | 仅 1 家 |
| cashier01 | CASHIER | 15 | 仅 1 家 |

拦截验证：
- 店长访问门店 999 → 10202 没有该门店的数据权限
- 店长删其它门店 → 10201 没有该操作权限
- 收银员删用户 → 10201 没有该操作权限
- 无 token → 401

BCrypt 密码散列原样迁移，历史账号可直接登录。

### API 兼容性

单体 24 个接口在微服务版一个不少，额外 4 个内部接口。前端无需改动。

### 关键修复

**缺 JwtAuthenticationFilter 导致所有管理接口 500** ——
权限上下文没有装进 Spring Security 的 Authentication，
所有 @PreAuthorize 都会拒绝。已迁移该过滤器并让 Gateway 透传 X-Permissions。

另补齐 24 个业务错误码（编号与单体一致）、PasswordEncoder Bean、LoginProperties。

---

## [第五阶段·任务三第一批] 数据迁移 + wallet 资金业务迁移（2026-11-19）

### 数据迁移

- 备份：`infrastructure/migration/backup/`（453 KB）
- 映射：`MIGRATION-MAPPING.md`（按服务边界分库，保留历史 ID）
- 执行器：`migrate.js`（--dry-run / --force），校验器：`verify.js`
- 跨实例必须走 `mysqldump | mysql` 管道，不能用 INSERT...SELECT
- **1625 行迁移完成，61 项校验全通过**
  - 29 张表行数一致
  - 钱包余额 255000/36338，流水/充值/订单/支付/退款金额全部一致
  - 资金不变量 I1~I6 全部成立
  - 历史 ID（钱包/流水/会员/SKU/订单/支付单/微信交易号）范围一致

### wallet-service 资金业务迁移

- 迁移 36 个文件（实体/枚举/DTO/Mapper/Service），表名列名不变
- 跨库依赖改造：品牌扣款顺序 → Feign 调 auth-service + Redis 缓存 60s
- MyMetaObjectHandler 迁到 common-core
- 新增内部接口 6 个（受 X-Internal-Token 保护）
- auth-service 新增品牌内部接口 2 个

### 测试

- 资金测试 11/11 通过（8 一致性 + 2 并发 + 1 装配）
- 并发实测：10 线程各扣 1500（余额 10000）→ 成功 6 失败 4，扣 9000 剩 1000
- 并发实测：8 线程同订单扣 3000 → 仅 1 条流水，扣 3000
- 端到端：wallet → auth Feign 调用成功，traceId 一致，Redis 缓存生效

### 重要教训

资金数据不能手工 DELETE（会破坏"余额 = 流水累计"不变量）。
测试数据必须用独立会员 ID 隔离，清理后复核不变量。

### 未完成

auth/member/product/order/payment 五个服务的业务代码迁移、
Outbox/Inbox、8 类事件发布、部分金额冻结能力。

---

## [第五阶段·任务三] Docker 容器化部署验证（2026-11-19）

### 环境突破

WSL 3.0.5.0 安装成功（内核 6.18.58.1），Docker Desktop 引擎 29.9.3 就绪。
配置镜像加速器（Docker Hub 被墙）后成功拉取全部镜像。

### 容器编排

- `deploy/docker-compose.yml`：4 中间件 + 7 应用服务，含健康检查、依赖顺序、命名网络、数据卷
- `deploy/nacos/conf/application.properties`：从镜像提取原始配置后精确修改，强制内嵌 Derby
  （镜像默认走 MySQL 分支，占位符未展开导致崩溃，排查 4 轮才定位）
- `deploy/mysql/init/01-create-databases.sql`：9 库 + 10 账号，密码与 `.env.docker` 同步
- 7 个服务的多阶段 Dockerfile（构建上下文为 `teaclub-cloud` 父目录）

### 实测

| 项 | 结果 |
| --- | --- |
| 4 个中间件容器 | ✅ 全部 healthy |
| 9 个数据库 | ✅ 自动创建 |
| 权限隔离 | ✅ `tc_wallet` 访问 `order_db` 被拒（ERROR 1142） |
| Redis | ✅ PING → PONG |
| RabbitMQ 管理台 | ✅ 认证通过 |

### 未完成

7 个应用服务容器构建中、Nginx 前置（前端目录为空）、各服务 Flyway 迁移、
应用服务连容器 Nacos 的注册验证。

---

## [第五阶段·任务二完成] Spring Cloud 微服务基础设施（2026-11-19）

### 目录调整

`web/` → `teaclub-admin/`，`miniapp/` → `teaclub-miniprogram/`，`deploy/` → `infrastructure/docker/`

### 新增模块

- `common-redis`：缓存/验证码/登录态/热点数据/分布式锁
- `common-feign`：客户端基类/超时/重试策略/异常解码

### 补齐能力

- common-core：参数校验组件 + 统一日志上下文（MDC）
- common-web：健康指示器
- common-messaging：消息消费者基类（手动 ACK/幂等/TraceId 恢复）+ 重试策略
- common-security：内部接口保护（FilterRegistrationBean + 路径段匹配 + 401 统一响应）
- Nacos 配置管理：teaclub-common/dev/test 三份共享配置
- auth-service：Feign 客户端 + 调用演示端点
- member-service：内部接口（受 X-Internal-Token 保护）

### 验收结果（11 项全通过）

编译 16 模块 / Gateway UP / Nacos OK / 注册 7/7 / 发现 healthy 7/7 /
路由 401-401-404-200 / OpenFeign 调用成功 / RabbitMQ 集成测试 4/4 /
Redis PONG / 11 个容器运行 / 跨库访问被拒 ERROR 1142

### 本轮修复 20 个问题

重点：内部接口此前无凭证可访问（过滤器路径匹配错 + 未注册进链）、
Feign 拦截器因 @ConditionalOnMissingBean 冲突未生效。

## [第五阶段·任务二] 微服务基础设施搭建（2026-11-19）

### 新增工程

`teaclub-cloud/` —— 15 个 Maven 模块：

- `common-core`（统一响应/错误码/异常/TraceId/单号生成，16 单元测试）
- `common-web`（TraceId 过滤器/全局异常/OpenAPI，条件装配隔离 Servlet 与 WebFlux）
- `common-security`（JWT/登录上下文/StoreScope/Feign 透传/内部接口保护）
- `common-messaging`（RabbitMQ 交换机/队列/死信/事件发布/消费幂等，4 项真实集成测试）
- `gateway-service`（路由/JWT 前置校验/限流/跨域/TraceId/全局异常）
- 9 个业务服务骨架（auth/member/product/wallet/order/payment/marketing/inventory/report）

### 版本矩阵（实测修正）

初选 Spring Cloud 2023.0.5 + Boot 3.5.16 → **启动失败**（SC 2023.0.x 只支持 Boot 3.2/3.3）。
修正为 **Spring Cloud 2025.0.3 + Spring Cloud Alibaba 2025.0.0.0 + Boot 3.5.16** → 启动成功。

Swagger annotations 升至 **2.2.55**（仅该版本含 `DateTimeLocalSchema`）。

### 中间件安装（绕过 Docker）

WSL 安装在 96.2% 下载失败，改用官方 Windows 原生版：

| 组件 | 版本 | 方式 | 验证 |
| --- | --- | --- | --- |
| Nacos | 2.5.1 | 官方 zip | ✅ 8848 UP，服务注册成功 |
| RabbitMQ | 3.13.7 | 官方 zip + Erlang/OTP 27.2 | ✅ 5672/15672 UP，4 项集成测试通过 |
| WSL | 3.0.5.0 | 用户提供的 MSI + 自提权脚本 | ✅ 内核 6.18.58.1-1 |

### 实测结果

- Gateway 九项能力全部通过（401/401/404/503 统一 Result 结构、`X-Trace-Id`）
- 完整链路：真实 JWT → Gateway → auth-service → 200
- Nacos 服务注册：count=1, healthy=True
- RabbitMQ 集成测试 4 项：拓扑/收发/Confirm/Returns
- 单元测试：common-core 16 + common-messaging 4
- 单体回归 99 项不受影响

### 未完成（明确标注）

OpenFeign 真实调用（未定义客户端）、其余 8 服务注册验证、Docker Compose（需重启后 WSL 生效）、
Nacos 配置中心实际拉取、各服务业务代码迁移。

---

## [第五阶段·任务一] 微服务架构审计与设计（2026-11-19）

### 新增文档（6 份）

- `docs/CURRENT-PROJECT-AUDIT.md` — 现有项目审计（实际读取代码与数据库）
- `docs/MICROSERVICES.md` — 微服务架构设计与版本矩阵
- `docs/SERVICE-BOUNDARIES.md` — 服务边界与数据所有权
- `docs/DATABASE-OWNERSHIP.md` — 数据库归属与权限隔离
- `docs/DATA-MIGRATION.md` — 数据迁移方案与黄金基线
- `docs/MICROSERVICE-MIGRATION-ROADMAP.md` — 9 步迁移路线图

### 审计实测结果

- 后端 220 个 Java 文件 / 12,498 行，17 Controller / 66 端点
- 19 Service / 27 Entity / 27 Mapper
- 数据库 33 张表，Flyway V8
- **前端与部署目录为 0 文件**（web / miniapp / deploy 均未开发）
- 测试资产：44 单元测试 + 199 集成断言

### 关键发现

1. **payment 直连 order/wallet 表**（`PaymentServiceImpl` 直接使用 `OmsOrderMapper`、`WalRechargeOrderMapper`）— 违反数据所有权，拆分头号难点
2. **order 直读 4 张 product 表** — 需改为 Feign 调 `/internal/products/quote`
3. **wallet/order 直读 member/system 表** — 需改为 Feign

### 版本矩阵（已实证）

`spring-boot 3.5.16` + `spring-cloud 2023.0.5` + `spring-cloud-alibaba 2023.0.3.4`
经 `mvn dependency:tree` 实测解析通过（BUILD SUCCESS）。

> 注：SCA 2025.1.x 需 Boot 4.x，与现有 3.5.16 不兼容，故选用 2023.0.x 线。

### 迁移黄金基线

```text
钱包实付余额合计 = 255000 分
钱包赠送余额合计 =  38000 分
流水实付变动合计 = 255000 分  （与余额相等）
流水赠送变动合计 =  38000 分  （与余额相等）
```

### 本轮未做的事（明确声明）

- ❌ 未修改任何业务代码（Java 文件 mtime 未变）
- ❌ 未执行任何数据库迁移
- ❌ 未删除或重写现有功能
- ❌ 未创建微服务工程

### 环境阻塞

- Git 仓库未初始化（迁移第一步需 `git init`）
- Nacos Server 未安装
- RabbitMQ 未安装
- Docker 已安装但 WSL 未启用，守护进程不可用

---

# 变更记录

格式参考 Keep a Changelog，版本号遵循语义化版本。

---

## [项目更名] 完成

- 项目名称由「奶茶店会员与储值销售系统」改为「奶茶店会员与储值营销系统」
- 根目录已重命名：`H:\奶茶店会员与储值营销系统`（318 个文件完整迁移）
- 更新文档与配置中的路径引用：README.md、CHANGELOG.md、docs/03、docs/06、backend/.idea/workspace.xml
- 说明：`Rename-Item` 在该目录上返回拒绝访问（疑似目录句柄/ACL 特性），改用 `Move-Item` 成功完成
- 验证：`mvn clean test` 全绿（12 个单元测试）、接口测试 99/99、越权隔离测试 19/19

---

## [第一阶段] 2026-11-19 — 需求分析与系统设计

### 新增

- 项目仓库初始化，创建 `docs/`、`backend/`、`web/`、`miniapp/`、`deploy/`、`scripts/` 目录骨架
- 设计文档 8 份：
  - 01-需求分析与功能清单
  - 02-系统架构设计
  - 03-数据库设计（含 41 张表完整 DDL）
  - 04-核心业务规则与资金安全设计
  - 05-接口规范与安全设计
  - 06-项目目录规划
  - 07-开发计划与里程碑
  - 08-环境预研与部署规划
- 项目 README、文档索引、`.gitignore`、`.env.example`

### 环境核查结论

- 目标目录 `H:\奶茶店会员与储值营销系统` 初始为空（递归文件数 0）
- **未发现 `teaclub.sql`**（H: 盘递归深度 4 检索无匹配），无历史 SQL 需审查
- 已确认可用：JDK 17.0.19、Maven 3.9.6、Node 22.23.1、MySQL 8.4（服务运行中）
- 已确认缺失：Docker、Redis、微信商户资质
- 已确认缺陷：`mvn` 命令被 Node 包装器劫持并报错，需使用 `apache-maven-3.9.6\bin\mvn.cmd`

### 技术选型决策

- Spring Boot 采用 **3.5.16**（而非 4.2.0-RC2，需求要求 3.x）
- SpringDoc 采用 **2.10.0**（3.x 面向 Boot 4，不兼容）
- Vite 7.3.8 / Pinia 3.0.4 / Vue Router 4.6.5 / TypeScript 5.9.3（均刻意避开最新大版本）

### 未包含（明确排除）

- 任何业务代码（按用户要求，本阶段只做规划与设计）
- 数据库实际创建（等待 MySQL 凭据确认）

---

## [第二阶段] 后端基础业务 — 已完成

### 新增（后端工程）

- Maven 工程 `backend/`：Spring Boot 3.5.16 + Java 17
- 依赖：MyBatis-Plus 3.5.18、Flyway 11.20.3、jjwt 0.13.0、SpringDoc 2.10.0、MySQL Driver、Redis Starter、Validation、AOP
- Flyway 迁移脚本 4 个（V1 系统 / V2 会员 / V3 商品 / V4 种子数据），已在本机 MySQL 8.4 实际执行成功（22 张表）

### 新增（功能模块，129 个 Java 文件 / 约 6510 行）

| 模块 | 内容 |
| --- | --- |
| 登录认证 | 账号密码登录、JWT 签发、登出、改密、当前用户、菜单树；连续 5 次失败锁定 15 分钟 |
| 用户管理 | 分页查询、详情、增删改、重置密码、启停、门店授权、角色绑定 |
| 角色管理 | 分页、列表、详情、增删改、权限分配；内置角色保护、被引用角色禁删 |
| 菜单权限 | 权限树、当前用户权限树、权限点增删改查（MENU/BUTTON/API 三类） |
| 按钮权限 | `@PreAuthorize("hasAuthority('...')")` 方法级鉴权，44 个权限点 |
| 多门店数据权限 | `StoreScope` + `StoreScopeResolver`，查询自动注入门店过滤、写操作强制断言 |
| 品牌管理 | 品牌信息查询与更新（含储值跨店开关、扣款顺序、赠送金有效期） |
| 门店管理 | 门店 CRUD、状态变更、可访问门店列表；门店级账号禁止增删门店 |
| 会员管理 | 分页查询（受门店过滤）、手机号查询、详情、资料修改、冻结解冻、删除；列表手机号脱敏 |
| 会员等级 | 列表、详情、增删改；折扣率/积分倍率校验、在用等级禁删 |
| 商品分类 | 树形分类查询、增删改；有子级或有商品禁删 |
| 商品管理 | SPU + SKU 一体化增删改查、上下架；编码唯一、成本价/保护价校验 |
| 门店商品 | 门店选品上下架、批量上下架、差异化定价（保护价校验）、移除；越权访问拒绝 |

### 基础能力

- 统一响应 `Result<T>`（code/message/data/traceId/timestamp）
- 统一分页 `PageResult<T>`
- 全局异常处理（业务异常、参数校验、唯一约束冲突、越权、认证失败、兜底）
- 错误码体系（5 位数字，按模块分段）
- TraceId 过滤器（MDC + 响应头）
- 操作日志 AOP（记录操作人、入参、耗时、结果；**递归脱敏敏感字段**）
- `CacheService` 双实现（local / redis），本机无 Redis 时用 local
- Swagger/OpenAPI（40 个接口路径）

### 数据库

- 本机 MySQL 8.4 创建独立库 `teaclub`（utf8mb4），专用账号 `teaclub`（仅本库权限）
- 种子数据：1 品牌 / 3 门店 / 5 账号 / 4 角色 / 44 权限点 / 4 会员等级 / 3 会员 / 4 分类 / 6 SPU / 11 SKU / 9 门店商品 / 17 门店定价

### 验证结果

- `mvn compile` 通过
- `mvn test` 通过（12 个单元测试）
- Spring Boot 启动成功，`/actuator/health` = UP
- 接口自动化测试 `scripts/api-test.mjs`：**99 项断言全部通过**，且可重复执行
- 多门店隔离验证：总部见 3 店、店长见 1 店、越权访问返回 10202
- 按钮权限验证：收银员访问员工管理/店长访问品牌配置均返回 10201

### 修复的缺陷

1. **SKU 编辑误判重复编码**：按 id 匹配导致编辑时无法复用自身编码 → 改为「id 优先、编码兜底」双重匹配
2. **软删除后唯一索引冲突导致 500**：改为显式统计含已删除数据的编码占用，返回明确业务错误（30002/20001）
3. **操作日志泄露明文密码**：脱敏仅检查顶层参数名，嵌套 DTO 未覆盖 → 改为递归 JSON 树脱敏，并清理历史泄露记录

---

## [待开始] 第三阶段 — 核心业务（储值/订单/支付/退款）

计划内容见 `docs/07-开发计划与里程碑.md` 第四阶段任务表。