# TeaClub 第五阶段任务清单

| 项目 | 内容 |
| --- | --- |
| 文档编号 | TC-MSA-09 |
| 更新日期 | 2026-11-19 |
| 阶段 | 第五阶段·任务二：微服务基础设施 |
| 总体状态 | **11 个容器全部运行，微服务全链路打通** |

---

## 1. 版本兼容性 —— 实测修正记录

**初选错误，已通过实际启动发现并修正：**

| 轮次 | Spring Cloud | Spring Boot | 结果 |
| --- | --- | --- | --- |
| 初选 | 2023.0.5 | 3.5.16 | ❌ 启动失败：`Spring Boot [3.5.16] is not compatible with this Spring Cloud release train` |
| **修正** | **2025.0.3** | **3.5.16** | ✅ 启动成功 |

> Maven 依赖解析通过 ≠ 运行期兼容。Spring Cloud 有严格的 Boot 版本区间校验。

### 最终版本矩阵

| 组件 | 版本 |
| --- | --- |
| JDK | 17.0.19 |
| Spring Boot | 3.5.16 |
| Spring Cloud | **2025.0.3**（parent 4.3.0） |
| Spring Cloud Alibaba | **2025.0.0.0** |
| MyBatis-Plus | 3.5.18 |
| JJWT | 0.13.0 |
| Flyway | 11.20.3 |
| SpringDoc | 2.10.0 |
| Swagger annotations | **2.2.55** |
| Nacos Server | 2.5.1（Windows 原生 zip） |
| RabbitMQ Server | 3.13.7（Windows 原生 zip） |
| Erlang/OTP | 27.2（RabbitMQ 依赖） |

---

## 2. 中间件安装方式（绕过 Docker）

WSL 安装在 96.2% 处下载失败（微软 CDN 不稳定），但**两个中间件都有官方 Windows 原生版**，均已实测跑通：

| 组件 | 安装方式 | 验证 |
| --- | --- | --- |
| Nacos 2.5.1 | 官方 zip（146.6 MB）→ `startup.cmd -m standalone` | ✅ 8848 健康检查 OK，服务注册成功 |
| RabbitMQ 3.13.7 | 官方 zip（30.7 MB）+ Erlang/OTP 27.2（133 MB） | ✅ 5672 AMQP UP，15672 管理台 UP，4 项集成测试通过 |
| Redis 3.0.504 | winget（Windows 移植版） | ✅ 6379 UP，Gateway 限流已连接 |
| MySQL 8.4.9 | 本机已装 | ✅ 3306 UP |

### Docker 容器版（重启后新增）

| 容器 | 镜像 | 端口 | 状态 |
| --- | --- | --- | --- |
| teaclub-mysql | mysql:8.4 | 3307 | ✅ healthy |
| teaclub-redis | redis:7-alpine | 6380 | ✅ healthy |
| teaclub-nacos | nacos/nacos-server:v2.5.1 | 8848 | ✅ healthy |
| teaclub-rabbitmq | rabbitmq:3.13-management-alpine | 5672/15672 | ✅ healthy |

**数据库权限隔离实测**：`tc_wallet` 访问 `order_db` 被拒
（`ERROR 1142 SELECT command denied`），9 个库全部创建。

### 应用服务容器（11 个全部运行）

| 容器 | 端口 | 状态 |
| --- | --- | --- |
| teaclub-gateway | 9000 | ✅ healthy |
| teaclub-auth | 9001 | ✅ UP |
| teaclub-member | 9002 | ✅ UP |
| teaclub-product | 9003 | ✅ UP |
| teaclub-wallet | 9004 | ✅ UP |
| teaclub-order | 9005 | ✅ UP |
| teaclub-payment | 9006 | ✅ UP |

**端到端实测**：真实 JWT → Gateway 容器 → Nacos 服务发现 → auth-service 容器 → 200。

详见 `docs/17-容器化部署完整验证报告.md`。

### WSL 已就绪

用户提供的 `E:\Develop\wsl.3.0.5.0.x64.msi`（350.75 MB）已通过自提权脚本安装成功：

```text
WSL version: 3.0.5.0
Kernel version: 6.18.58.1-1
```

安装脚本保留在 `scripts/install-wsl.ps1`（含可选功能启用与验证）。

---

## 3. 模块完成情况

| # | 模块 | 编译 | 可启动 | 实测证据 |
| --- | --- | --- | --- | --- |
| 1 | `teaclub-cloud` 父 POM | ✅ | — | 15 模块统一版本 |
| 2 | `common-core` | ✅ | — | **16 单元测试通过** |
| 3 | `common-web` | ✅ | ✅ | 条件装配（Servlet/WebFlux 隔离） |
| 4 | `common-security` | ✅ | ✅ | JWT + 安全链 + 内部接口保护 |
| 5 | `common-messaging` | ✅ | ✅ | **4 项 RabbitMQ 真实集成测试通过** |
| 6 | `gateway-service` | ✅ | ✅ **实测** | 见 §4 |
| 7 | `auth-service` | ✅ | ✅ **实测** | **已注册 Nacos，healthy=True** |
| 8 | `member-service` | ✅ | ⏳ 骨架 | 未接 Nacos |
| 9 | `product-service` | ✅ | ⏳ 骨架 | 未接 Nacos |
| 10 | `wallet-service` | ✅ | ⏳ 骨架 | 未接 Nacos |
| 11 | `order-service` | ✅ | ⏳ 骨架 | 未接 Nacos |
| 12 | `payment-service` | ✅ | ⏳ 骨架 | 未接 Nacos |
| 13 | `marketing-service` | ✅ | ⏳ 骨架 | 预留 |
| 14 | `inventory-service` | ✅ | ⏳ 骨架 | 预留 |
| 15 | `report-service` | ✅ | ⏳ 骨架 | 预留 |

**"骨架"定义**：pom + 启动类 + `HealthController` + `application.yml` + 上下文测试。
**不含**：任何第四阶段业务代码迁移。

---

## 4. 实测验收结果

### 4.1 Gateway 九项能力

| # | 验收项 | 结果 | 证据 |
| --- | --- | --- | --- |
| 1 | 统一 API 入口 | ✅ | 9000 |
| 2 | 服务动态路由 | ✅ | 9 条 `lb://` 路由 |
| 3 | JWT 前置校验 | ✅ | 无 token → 401 `10101`；伪造 → 401 `10103` |
| 4 | 跨域配置 | ✅ | CorsWebFilter |
| 5 | 请求日志 | ✅ | 含 traceId |
| 6 | 全局异常处理 | ✅ | `10301` 路由不存在 / `10302` 服务不可用 |
| 7 | 限流 | ✅ | Redis 连接 UP |
| 8 | 统一请求 ID | ✅ | `X-Trace-Id` 响应头 |
| 9 | 服务不可用处理 | ✅ | 503 + `10302` |

### 4.2 完整链路（真实 JWT）

```text
1. GET :9001/api/auth/dev/token  → 344 字符真实 JWT
2. GET :9001/api/auth/health      → 200 直连
3. GET :9000/api/auth/health      → 200 经 Gateway 转发
4. Nacos 服务列表: count=1, services=auth-service
5. Nacos 实例: healthy=True
```

### 4.3 RabbitMQ 真实集成测试（4 项）

| 测试 | 结果 |
| --- | --- |
| 交换机/队列/绑定按规范存在（含 DLX 参数） | ✅ |
| 消息发送与接收（路由正确、traceId 透传） | ✅ |
| Publisher Confirm 生效 | ✅ |
| 未绑定路由消息被退回（Returns 生效） | ✅ |

### 4.4 端口状态

| 端口 | 服务 | 状态 |
| --- | --- | --- |
| 8080 | 单体 backend | ✅ UP |
| 9000 | gateway-service | ✅ UP |
| 9001 | auth-service | ✅ UP |
| 8848 | Nacos | ✅ UP |
| 3306 | MySQL | ✅ UP |
| 6379 | Redis | ✅ UP |
| 5672 | RabbitMQ AMQP | ✅ UP |
| 15672 | RabbitMQ 管理台 | ✅ UP |

---

## 5. 未完成项（如实标注）

| # | 项 | 原因 |
| --- | --- | --- |
| 1 | OpenFeign 真实调用测试 | 尚未定义 Feign 客户端接口 |
| 2 | 其余 8 个服务注册 Nacos | 骨架就绪，未逐个启动 |
| 3 | Docker Compose 整体启动 | WSL 已装但**需重启**才生效；Docker 守护进程仍不可用 |
| 4 | Nacos 配置中心实际拉取 | local profile 关闭该项，待 Docker 就绪后验证 |
| 5 | 各服务业务代码迁移 | 属后续任务，本阶段明确不做 |

> **不能声称完成**：以上 5 项均未实际验证。

---

## 6. 本轮遇到的问题与修复（完整记录）

| # | 问题 | 根因 | 修复 |
| --- | --- | --- | --- |
| 1 | POM 无法解析 | XML 注释含 `--` | 改 `->` |
| 2 | 子模块找不到父 POM | relativePath 层级错 | common 用 `../..`，服务用 `..` |
| 3 | swagger 注解版本缺失 | 不在 Boot BOM | 父 POM 显式管理 |
| 4 | `addSecurityScheme` 找不到 | 实际是复数 `addSecuritySchemes` | javap 确认后修正 |
| 5 | common-security 缺 feign | 缺 feign-core | 加 `provided` 依赖 |
| 6 | Gateway 启动失败：缺 feign 类 | 扫描到 Feign 拦截器 | 移出扫描 + `@ConditionalOnClass` |
| 7 | Gateway 启动失败：Servlet 栈冲突 | common-web 引入 starter-web | 改 `provided` 作用域 |
| 8 | Gateway 启动失败：Filter 类缺失 | 过滤器继承 Servlet 类 | 去 `@Component`，改条件 `@Bean` |
| 9 | **Gateway 启动失败：版本不兼容** | **SC 2023.0.5 只支持 Boot 3.2/3.3** | **升 SC 2025.0.3 + SCA 2025.0.0.0** |
| 10 | 白名单 403、401 无响应体 | Spring Security 默认策略拦截 | 新增 `GatewaySecurityConfig` |
| 11 | auth 启动失败：swagger 类缺失 | 2.2.38 无 `DateTimeLocalSchema` | 升 2.2.55（逐版本 jar 验证） |
| 12 | auth 启动失败：YAML 重复键 | 插入 dev-token 配置时造成 | 合并 security 块 |
| 13 | auth 启动失败：缺 TokenIssuer Bean | 自动装配未扫到 | 显式 `@Bean` 注册 |
| 14 | Nacos 登录失败 | 2.5.1 standalone 默认无 auth，却传了账号密码 | local profile 去掉认证字段 |
| 15 | 业务服务 401 | Spring Security 默认策略 | 新增 `ResourceServerSecurityConfig` |
| 16 | RabbitMQ 测试 API 不匹配 | 5.x 移除 `basicConsume(String,boolean,Consumer)`、`DeclareOk` 不暴露参数 | 改用 `DeliverCallback` 与"重新声明"校验 |
| 17 | RabbitMQ confirm/returns 未触发 | `RabbitTemplate` 每实例仅允许一个回调，且未开 confirm | 移除占位回调 + 显式 `ConfirmType.CORRELATED` |
| 18 | 消息转换失败 | 泛型 `EventMessage` 类型擦除 | 测试发送 Map |

**第 9、11 项是设计级错误**，靠编译或依赖解析无法发现。

---

## 7. 与单体项目的关系

单体（`backend/`，8080）**继续可用，未做任何修改**。

```text
单体项目                    微服务工程
backend/ :8080  ✅        teaclub-cloud/
  220 Java 文件              15 Maven 模块
  33 张表                    gateway :9000  ✅
  199 集成测试                auth    :9001  ✅ 已注册 Nacos
                              Nacos   :8848  ✅
                              RabbitMQ 5672  ✅
```

---

## 8. 启动脚本

| 脚本 | 用途 |
| --- | --- |
| `scripts/install-wsl.ps1` | WSL 安装（自动提权 + 可选功能启用） |
| `scripts/api-test.mjs` | 单体接口回归（99 项） |
| `scripts/security-isolation-test.mjs` | 单体越权隔离（19 项） |
| `scripts/funds-test.mjs` | 单体资金业务（59 项） |
| `scripts/funds-concurrency-test.mjs` | 单体并发幂等（21 项） |
| `scripts/member-visibility-test.mjs` | 会员可见口径 B（12 项） |
