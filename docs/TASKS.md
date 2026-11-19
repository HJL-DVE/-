# TeaClub 第五阶段任务清单

| 项目 | 内容 |
| --- | --- |
| 文档编号 | TC-MSA-09 |
| 更新日期 | 2026-11-19 |
| 阶段 | 第五阶段·任务二：Spring Cloud 微服务基础设施搭建 |
| 总体状态 | **11 项验收全部通过** |

---

## 1. 验收测试结果（11 项，全部实测）

| # | 验收项 | 结果 | 实测证据 |
| --- | --- | --- | --- |
| 1 | Maven 全工程编译 | ✅ | 16 个模块全部 BUILD SUCCESS |
| 2 | Gateway 启动 | ✅ | `/actuator/health` → status=UP，端口 9000 |
| 3 | Nacos 启动 | ✅ | `/nacos/v1/console/health/readiness` → OK，端口 8848 |
| 4 | 服务注册测试 | ✅ | 已注册 7/7：gateway/auth/member/product/wallet/order/payment |
| 5 | 服务发现测试 | ✅ | healthy 7/7，容器 IP 全部可达 |
| 6 | Gateway 路由测试 | ✅ | 无 token=401、伪 token=401、未路由=404、真实 token=200 |
| 7 | OpenFeign 调用测试 | ✅ | auth→member 调用成功，下游返回 member-service，traceId 透传 |
| 8 | RabbitMQ 发送和接收测试 | ✅ | **真实集成测试 4/4 通过**（拓扑/收发/Confirm/Returns），0 跳过 |
| 9 | Redis 连接测试 | ✅ | `redis-cli PING` → PONG |
| 10 | Docker Compose 启动测试 | ✅ | 11/11 容器运行中 |
| 11 | 数据库权限隔离 | ✅ | 跨库访问被拒（ERROR 1142 SELECT command denied） |

---

## 2. 模块完成情况

| # | 模块 | 编译 | 可启动 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | `teaclub-cloud`（父 POM） | ✅ | — | 16 模块统一版本管理 |
| 2 | `common-core` | ✅ | — | 统一响应/错误码/异常/TraceId/单号/**参数校验**/**日志上下文** |
| 3 | `common-web` | ✅ | ✅ | TraceId 过滤器/全局异常/OpenAPI/**健康指示器** |
| 4 | `common-security` | ✅ | ✅ | JWT/上下文/StoreScope/Feign 透传/**内部接口保护** |
| 5 | `common-messaging` | ✅ | ✅ | Exchange/Queue/DLX/**消费者基类**/**重试策略**/幂等守卫 |
| 6 | `common-redis` | ✅ | — | **新增**：缓存/验证码/登录态/热点数据/**分布式锁** |
| 7 | `common-feign` | ✅ | — | **新增**：客户端基类/超时/**重试策略**/异常解码 |
| 8 | `gateway-service` | ✅ | ✅ | 统一入口/动态路由/JWT/跨域/日志/异常/限流/TraceId/降级 |
| 9 | `auth-service` | ✅ | ✅ | 已注册 Nacos，**已定义 Feign 客户端** |
| 10 | `member-service` | ✅ | ✅ | 已注册 Nacos，**已提供内部接口** |
| 11 | `product-service` | ✅ | ✅ | 已注册 Nacos |
| 12 | `wallet-service` | ✅ | ✅ | 已注册 Nacos |
| 13 | `order-service` | ✅ | ✅ | 已注册 Nacos |
| 14 | `payment-service` | ✅ | ✅ | 已注册 Nacos |
| 15 | `marketing-service` | ✅ | ⏳ 骨架 | 预留，未注册 Nacos |
| 16 | `inventory-service` | ✅ | ⏳ 骨架 | 预留，未注册 Nacos |
| 17 | `report-service` | ✅ | ⏳ 骨架 | 预留，未注册 Nacos |

**"骨架"定义**：pom + 启动类 + `HealthController` + `application.yml` + 上下文测试。
**不含**：任何第四阶段业务代码迁移。

---

## 3. 目录结构（已按规格调整）

```text
TeaClub/
├── backend/              单体后端（保留可用，未改动）
├── teaclub-cloud/
│   ├── pom.xml
│   ├── common/
│   │   ├── common-core/      统一响应/异常/错误码/TraceId/单号/参数校验/日志上下文
│   │   ├── common-web/       过滤器/全局异常/OpenAPI/健康指示器
│   │   ├── common-security/  JWT/上下文/权限/内部接口保护/Feign 透传
│   │   ├── common-messaging/ RabbitMQ 组件 + 消费者基类 + 重试策略
│   │   ├── common-redis/     缓存/验证码/登录态/热点数据/分布式锁
│   │   └── common-feign/     OpenFeign 基类/超时/重试/异常解码
│   ├── gateway-service/
│   └── 9 个业务服务
├── teaclub-admin/        管理后台前端（原 web/）
├── teaclub-miniprogram/  小程序前端（原 miniapp/）
├── infrastructure/       容器化部署编排（原 deploy/）
│   └── docker/
│       ├── docker-compose.yml
│       ├── .env.docker            （已 gitignore）
│       ├── mysql/init/
│       ├── nacos/conf/
│       └── nacos/config/          Nacos 共享配置（dev/test/common）
└── docs/
```

---

## 4. 各组件实现要点

### 4.1 Nacos

| 项 | 实现 |
| --- | --- |
| 服务注册 | 7 个服务全部注册，healthy=true |
| 服务发现 | 容器 IP 全部可达 |
| 配置管理 | `infrastructure/docker/nacos/config/`：`teaclub-common.yaml`、`teaclub-dev.yaml`、`teaclub-test.yaml` |
| 开发环境配置 | `application-local.yml`（各服务独立）+ `teaclub-dev.yaml` |
| 测试环境配置 | `teaclub-test.yaml` + `application-docker.yml` |
| 健康检查 | `/actuator/health` + 容器 healthcheck + Nacos readiness |

服务通过 `spring.config.import=optional:nacos:teaclub-common.yaml` 拉取共享配置，
`optional:` 前缀保证 Nacos 不可用时不阻断启动。

### 4.2 Gateway

九项能力全部实测通过：

| 能力 | 实现 |
| --- | --- |
| 统一 API 入口 | 9000 端口 |
| 服务动态路由 | 9 条 `lb://` 路由（`/api/{service}/**`） |
| JWT 认证前置校验 | `JwtAuthGlobalFilter` |
| 跨域配置 | `CorsWebFilter` |
| 请求日志 | traceId 进 MDC |
| 全局异常处理 | 统一 `Result` 结构 |
| 限流 | Redis `KeyResolver` |
| 统一请求 ID | `X-Trace-Id` 头 |
| 服务不可用处理 | 503 + `code=10302` |

> 网关<b>不含</b>业务逻辑。功能权限由各服务 `@PreAuthorize` 校验，
> 数据范围由各服务 `StoreScope` 校验。

### 4.3 OpenFeign

| 项 | 实现 |
| --- | --- |
| 服务间 REST 调用 | `MemberInternalClient`（auth → member），实测成功 |
| 统一超时配置 | `teaclub.feign.connect-timeout/read-timeout` |
| 调用异常处理 | `FeignCallExceptionHandler` → 统一 `BizException` |
| 重试策略 | `FeignRetryPolicy`，**默认 0 次** |
| Trace ID 传递 | `FeignRequestInterceptor` 写入 `X-Trace-Id` |
| 用户身份上下文传递 | 透传 `X-User-Id/X-Username/X-Roles/X-Store-Ids/X-Brand-Id` |

**重试约束（已落实）**：
- 默认 `default-retry: 0`
- `retryable-prefixes` 白名单只含 GET 只读接口
- 资金扣款/支付创建/退款<b>不得</b>加入白名单，必须靠业务单号幂等 + 结果查询

### 4.4 RabbitMQ

| 项 | 实现 |
| --- | --- |
| 消息生产者 | `EventPublisher` |
| 消息消费者 | `AbstractEventConsumer`（手动 ACK、幂等、TraceId 恢复、异常分类） |
| Exchange / Queue / Routing Key | `MqConstants` 统一定义 |
| Publisher Confirm | `CachingConnectionFactory.ConfirmType.CORRELATED` |
| 消费端 ACK | 手动 ACK，nack(requeue) 区分业务异常/系统异常 |
| 重试策略 | `MessageRetryPolicy`（默认 1s/5s/30s，3 次后死信） |
| 死信队列 | DLX + DLQ 已声明 |
| 消息幂等 | `MessageIdempotencyGuard`（Redis 占位 72h） |

**实测**：真实集成测试 4/4（拓扑/收发/Confirm/Returns），0 跳过。

### 4.5 Redis

| 项 | 实现 |
| --- | --- |
| 登录缓存 | `LoginSessionCache`（会话、失败计数、退出登录失效） |
| 热点商品缓存 | `HotDataCache`（SPU/分类/门店商品） |
| 门店缓存 | `HotDataCache`（门店 + 品牌） |
| 验证码 | `CaptchaService`（图形 + 短信，一次性使用） |
| 分布式锁 | `RedisDistributedLock`（SET NX PX + Lua 原子释放 + token 校验） |
| 缓存过期策略 | `RedisKeys` 集中定义 TTL |

**重要限制（已写入代码注释）**：Redis 分布式锁<b>不是</b>钱包资金一致性的唯一保障。
资金操作必须依赖数据库行锁 + 业务单号唯一索引。

### 4.6 Docker Compose

| 项 | 实现 |
| --- | --- |
| 服务健康检查 | 4 个中间件 + gateway 均配置 healthcheck |
| 数据持久化 | 5 个命名数据卷 |
| 环境变量 | `.env.docker`（已 gitignore，不提交真实密码） |
| 容器网络 | `teaclub-net` 桥接网络 |
| 日志管理 | json-file 驱动 + 容器内 logback 按服务分文件 |
| 启动依赖 | `depends_on` + `condition: service_healthy` |

---

## 5. 本轮修复的问题（完整记录）

| # | 问题 | 根因 | 修复 |
| --- | --- | --- | --- |
| 1 | Nacos 容器反复重启 | 镜像内 `db.url.0` 含 `${MYSQL_SERVICE_HOST}` 占位符 | 从镜像提取原始配置，精确改 4 行为内嵌 Derby |
| 2 | 镜像构建超时 | 容器内 Maven 需重下依赖 | 改为宿主机构建 + 轻量镜像（14s） |
| 3 | `COPY report-service` not found | 构建上下文是子模块 | 上下文改为模块目录 |
| 4 | `build.volumes` 非法 | compose build 块不支持 | 移除 |
| 5 | `COPY target/*.jar` 找不到 | `.dockerignore` 排除了 target | 修正 .dockerignore |
| 6 | gateway 容器启动失败 | 缺 `spring.config.import=nacos:...` | docker profile 补 config.import |
| 7 | 客户端报 `User nacos not found` | Nacos 关认证但客户端仍带账号密码 | 移除客户端认证参数 |
| 8 | dev-token 端点 404 | `DEV_TOKEN_ENDPOINT` 未进 common-env | 加入锚点 |
| 9 | token 端点 500 | docker profile 缺 `teaclub.jwt` | 补 jwt 段 |
| 10 | 内部接口无凭证可访问 | 过滤器只匹配 `/internal/`，实际是 `/api/x/internal/` | 改用 `contains("/internal/")` |
| 11 | 过滤器注册不生效 | `@Bean` 声明的 Filter 不自动进链 | 改用 `FilterRegistrationBean` 显式注册 |
| 12 | 内部凭证无效返回 500 | 过滤器抛 BizException 被当 500 | 直接写 401 + 统一 Result |
| 13 | Feign 调用不带内部令牌 | `@ConditionalOnMissingBean` 与 `@Configuration` 冲突 | 去掉注解冲突，显式 `@Import` |
| 14 | 用户上下文不透传 | 同上，拦截器未注册 | 移除 `@Configuration`，由自动配置注册 |
| 15 | common-feign 编译失败 | 缺 lombok 依赖 | 补依赖 |
| 16 | Retryer 编译失败 | Feign 13 接口为 `continueOrPropagate` | 按实际接口重写 |
| 17 | Result.getCode() 空判断 | 返回 int 原始类型 | 去掉 null 判断 |
| 18 | common-web 编译失败 | 缺 actuator 依赖 | 加 `optional` 依赖 |
| 19 | Nacos 容器启动失败 | 目录迁移后挂载路径失效 | 重建容器 |
| 20 | `.env.docker` 泄露到 Git | 上轮提交时被跟踪 | 已轮换全部密钥 + 重写 Git 历史 + 强制推送 |

---

## 6. 未完成项（如实标注）

| # | 项 | 原因 |
| --- | --- | --- |
| 1 | marketing / inventory / report 三个服务注册 Nacos | 骨架已就绪，未逐个启动验证 |
| 2 | Nginx 前置代理 | `teaclub-admin/` 无静态资源 |
| 3 | 各服务 Flyway 迁移 | 业务表未建（第四阶段任务） |
| 4 | RabbitMQ 服务间事件端到端 | 服务间事件属第四阶段 |
| 5 | 前端（admin / miniprogram） | 规格未要求实现业务前端 |
| 6 | 真实微信支付 | 无商户资质 |

> **不能声称完成**：以上 6 项均未实际验证。

---

## 7. 与单体项目的关系

单体（`backend/`，8080）**继续可用，未做任何修改**。

```text
单体项目                    微服务工程
backend/ :8080             teaclub-cloud/
  220 Java 文件              16 Maven 模块
  33 张表                    11 个容器运行中
  199 集成测试                gateway :9000  ✅
                             auth    :9001  ✅ 已注册 Nacos
                             member  :9002  ✅ 已提供内部接口
                             Nacos   :8848  ✅
                             RabbitMQ 5672  ✅ 4 项集成测试通过
                             MySQL   3307  ✅ 9 库 + 权限隔离
                             Redis   6380  ✅
```

---

## 8. 常用命令

```bash
# 编译
cd teaclub-cloud
mvn -B -DskipTests clean install

# 启动容器
cd infrastructure/docker
docker compose -f docker-compose.yml --env-file .env.docker up -d --build

# 查看状态
docker compose -f docker-compose.yml --env-file .env.docker ps

# 单元 + 集成测试
RABBITMQ_HOST=127.0.0.1 RABBITMQ_PORT=5672 \
RABBITMQ_USER=teaclub RABBITMQ_PASSWORD=<密码> RABBITMQ_VHOST=/teaclub \
mvn -B -pl common/common-messaging -am test
```
