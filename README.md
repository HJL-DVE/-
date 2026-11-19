# TeaClub 连锁奶茶店会员与储值营销管理系统

## 项目结构

```text
TeaClub/
├── backend/              单体后端（保留可用，未改动）
├── teaclub-cloud/        Spring Cloud 微服务工程
│   ├── pom.xml           父 POM，统一管理依赖版本
│   ├── common/
│   │   ├── common-core/      统一返回/异常/错误码/TraceId/单号
│   │   ├── common-web/       过滤器/全局异常/OpenAPI
│   │   ├── common-security/  JWT/上下文/权限/内部接口保护
│   │   ├── common-messaging/ RabbitMQ 组件
│   │   ├── common-redis/     Redis 缓存与分布式锁
│   │   └── common-feign/     OpenFeign 客户端基类
│   ├── gateway-service/  统一网关
│   └── 9 个业务服务        auth/member/product/wallet/order/payment/marketing/inventory/report
├── frontend/              前端工程（与后端完全分离）
│   ├── teaclub-admin/     Vue 3 + TS + Element Plus 管理后台
│   └── teaclub-miniprogram/ uni-app + Vue 3 + TS 微信小程序
├── infrastructure/       容器化部署编排
└── docs/                 设计与验收文档
```

> 一个品牌 · 多家门店 · 统一会员身份
> Web 管理后台 + 微信小程序 · 前后端分离 · 模块化单体

![status](https://img.shields.io/badge/阶段-第一阶段%20设计完成-blue)
![jdk](https://img.shields.io/badge/JDK-17-orange)
![boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-green)
![vue](https://img.shields.io/badge/Vue-3.5.45-42b883)

---

## 一、项目简介

TeaClub 是面向连锁奶茶品牌的会员与储值营销管理系统，覆盖 **总部统一管理 → 门店经营 → 会员消费** 三层业务闭环。

核心能力：

| 能力域 | 内容 |
| --- | --- |
| 多门店组织 | 总部 / 门店两级组织，员工门店授权，数据权限后端强制校验 |
| 会员储值 | 实付余额与赠送余额分列记账，每次变动生成不可篡改的资金流水 |
| 订单交易 | 小程序下单 + 收银台下单，服务端统一重算金额，订单状态机约束 |
| 微信支付 | 微信支付 API v3 适配器 + 无资质时的 Mock 适配器（严格隔离） |
| 营销体系 | 优惠券、积分、会员等级折扣、充值赠送、签到、活动配置 |
| 经营分析 | 全部指标由真实业务数据聚合，无任何硬编码或随机数 |

---

## 二、当前进度

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| 第一阶段 | 需求分析与系统设计 | ✅ **已完成** |
| 第二阶段 | 工程初始化 | ⏳ 待开始 |
| 第三阶段 | 基础业务 | ⏳ 待开始 |
| 第四阶段 | 核心业务（储值/订单/支付/退款） | ⏳ 待开始 |
| 第五阶段 | 营销业务 | ⏳ 待开始 |
| 第六阶段 | 前端开发（Web + 小程序） | ⏳ 待开始 |
| 第七阶段 | 测试与部署 | ⏳ 待开始 |

> 严格遵守"每阶段验收通过后才进入下一阶段"的推进规则。

---

## 三、技术栈

### 后端
- Java 17（Temurin 17.0.19）
- Spring Boot **3.5.16** / Spring Security / JWT（jjwt 0.13.0）
- MyBatis-Plus **3.5.18**
- MySQL **8.4** / Redis / Flyway **11.20.3**
- Spring Validation / SpringDoc OpenAPI **2.10.0**
- JUnit 5 / Mockito
- Maven 3.9.6

### Web 前端
- Vue **3.5.45** / TypeScript **5.9.3** / Vite **7.3.8**
- Element Plus **2.14.8** / Pinia **3.0.4** / Vue Router **4.6.5**
- Axios **1.21.1** / ECharts **6.1.0**

### 微信小程序
- uni-app（Vue 3 + TypeScript）/ Pinia

### 部署
- Docker / Docker Compose / Nginx / MySQL 8.4 / Redis 7

> 所有版本均在设计阶段通过仓库元数据实时核验，未凭记忆填写。考据过程见 `docs/08`。

---

## 四、文档索引

全部设计文档位于 `docs/`：

| 文档 | 说明 |
| --- | --- |
| [01-需求分析与功能清单](docs/01-需求分析与功能清单.md) | 角色定义、业务流程、功能清单、业务规则、NFR |
| [02-系统架构设计](docs/02-系统架构设计.md) | 架构图、分层、认证授权、资金一致性、支付适配器、ADR |
| [03-数据库设计](docs/03-数据库设计.md) | E-R 图、41 张表完整 DDL、索引与并发设计、统计口径 |
| [04-核心业务规则与资金安全设计](docs/04-核心业务规则与资金安全设计.md) | 钱包原子操作、退款矩阵、状态机、对账、测试用例 |
| [05-接口规范与安全设计](docs/05-接口规范与安全设计.md) | URL/响应/错误码规范、接口清单、报文示例、安全设计 |
| [06-项目目录规划](docs/06-项目目录规划.md) | 全仓库目录结构、命名规范、Git 规范 |
| [07-开发计划与里程碑](docs/07-开发计划与里程碑.md) | 阶段任务、验收标准、风险、质量门禁 |
| [08-环境预研与部署规划](docs/08-环境预研与部署规划.md) | 本机环境实测、已知问题、部署三方案、环境变量 |
| [11-代码质量标准与验收清单](docs/11-代码质量标准与验收清单.md) | 15 条硬性质量要求、汇报模板、待确认业务规则 |

---

## 五、目录结构

```
奶茶店会员与储值营销系统/
├── docs/        设计与交付文档
├── backend/     Spring Boot 3.5 后端（阶段二创建）
├── web/         Vue 3 管理后台（阶段二创建）
├── miniapp/     uni-app 微信小程序（阶段二创建）
├── deploy/      Docker Compose / Nginx 编排（阶段二创建）
└── scripts/     开发与运维脚本（阶段二创建）
```

---

## 六、本机环境现状（实测）

| 组件 | 状态 |
| --- | --- |
| JDK 17 / Node 22 / Maven 3.9.6 / MySQL 8.4 | ✅ 可用 |
| Docker / Redis | ❌ 未安装 |
| 微信商户资质 | ❌ 无 |

**应对策略**：
- 缓存：`CacheService` 抽象，开发默认 `local`，生产用 Redis
- 支付：`PaymentGateway` 接口 + `MockPaymentGateway`，真实适配器骨架保留
- 部署：Compose 编排文件交付 + 本机直跑方案实测

详见 [08-环境预研与部署规划](docs/08-环境预研与部署规划.md)。

---

## 七、核心设计原则

1. **金额一律用 BIGINT 存分**，禁止浮点。
2. **实付余额与赠送余额分列**，永不混记。
3. **每次余额变动必生成流水**，流水含变动前/变动额/变动后，不可修改删除。
4. **禁止直接 UPDATE 余额**，只能通过 `WalletService` 的原子方法。
5. **退款按原资金构成原路回冲**，不做金额覆盖。
6. **所有金额服务端重算**，不信任客户端传入的价格与余额。
7. **支付成功以后端验签通知/主动查单为准**，不以前端回调为准。
8. **门店数据权限后端强制校验**，前端隐藏菜单仅是体验优化。
9. **幂等键 + 行锁 + 唯一索引三重防护**，杜绝重复入账。
10. **统计数据全部由真实业务表聚合**，不接受任何硬编码。

---

## 八、开发约定

```text
Controller  仅做 HTTP 语义转换与参数校验
Service     业务编排与事务边界
Mapper      仅数据库访问
DTO / VO    出入参，Entity 不直接暴露
```

- 统一响应格式 `Result<T>{code, message, data, traceId, timestamp}`
- 统一异常处理 `GlobalExceptionHandler`
- 全部接口提供 Swagger 文档
- 密钥仅通过环境变量注入，仓库不含真实凭据

---

## 九、快速开始（第一阶段仅可阅读设计文档）

```powershell
# 目前仓库仅包含设计文档，代码工程在第二阶段创建
Get-ChildItem -Path .\docs
```

第二阶段完成后，此处将补充：后端启动、Web 启动、小程序编译、Docker 部署的完整命令。

---

## 十、已知限制（如实声明）

1. Docker Compose 编排文件将交付，但**本机未安装 Docker，无法实测拉起容器**。
2. **真实微信支付未联调**（无商户号/证书/APIv3 密钥），支付流程通过 Mock 适配器验证。
3. 本机**未安装 Redis**，相关能力以 `CacheService` 的 local 实现验证。
4. 微信小程序**未真机验证**（无 AppID），以编译通过为准。

以上限制不会以"已完成/已通过"的方式表述。

---

## 十一、许可

本项目为课程设计与软件工程展示用途。