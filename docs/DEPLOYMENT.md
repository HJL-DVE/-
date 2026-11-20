# TeaClub 部署手册（DEPLOYMENT）

| 项目 | 内容 |
| --- | --- |
| 文档编号 | TC-T9-02 |
| 环境 | Docker Compose 本地部署（课程设计） |

---

## 一、前置条件

```text
Docker Desktop（含 compose v2）≥ 4.30
Node.js ≥ 20（构建前端/跑测试）
JDK 17 + Maven（仅重新构建后端时需要）
内存建议 ≥ 8GB（WSL2 用户建议 .wslconfig 限制 memory=8GB）
```

## 二、目录结构

```text
teaclub/
├─ backend/teaclub-cloud/          # 9 个微服务源码（Maven 多模块）
├─ backend/infrastructure/docker/  # 部署编排（本手册主角）
│   ├─ docker-compose.yml          # 主编排：mysql/redis/nacos/rabbitmq/gateway/8 服务
│   ├─ docker-compose.web.yml      # admin-web overlay
│   ├─ .env.example                # 环境变量示例（复制为 .env.docker）
│   ├─ mysql/init/                 # 首次建库 SQL（幂等）
│   └─ README.md                   # 部署速查
├─ deploy/web/nginx.conf           # admin-web nginx 同源反代
├─ deploy/sql/                     # 权限点等运维 SQL（幂等可重跑）
└─ frontend/                       # teaclub-admin（Vue3）+ teaclub-miniprogram（uni-app）
```

## 三、首次部署步骤

### 3.1 环境变量

```bash
cd backend/infrastructure/docker
copy .env.example .env.docker        # Windows（Linux/Mac: cp）
# 编辑 .env.docker：把所有 change_me_* 改为真实值
# JWT 密钥建议：openssl rand -base64 48 生成
```

> ⚠️ `.env.docker` 含真实密码，**不要提交到 git**（已在 .gitignore）。

### 3.2 启动全部容器

```bash
docker compose -f docker-compose.yml -f docker-compose.web.yml \
  --env-file .env.docker up -d --build
```

首次启动 MySQL 自动执行：

1. `mysql/init/01-create-databases.sql` —— 建 10 个库 + 业务账号（tc_auth 等）
2. `mysql/init/02-tc-migrator-readonly.sql` —— 跨库只读账号
3. `mysql/init/03-report-db.sql` —— report_db + tc_report 跨库只读授权
4. 各服务 Flyway 自动迁移建表 + 种子数据（等级/套餐/权限点/券模板）

### 3.3 健康检查

```bash
docker ps                          # 14 个容器应全部 Up（gateway/mysql/redis/nacos/rabbitmq healthy）
curl http://localhost:9000/api/report/health    # 需登录，先登录拿 token
```

各服务启动顺序由 depends_on + healthcheck 保证；全部就绪约 60-90 秒。

## 四、访问入口

| 入口 | 地址 | 账号 |
| --- | --- | --- |
| Web 管理后台 | http://localhost:8080 | admin / TeaClub@123 |
| Gateway API | http://localhost:9000 | JWT Bearer |
| Nacos 控制台 | http://localhost:8848 | nacos/nacos |
| RabbitMQ 管理台 | http://localhost:15672 | teaclub / 见 .env |
| MySQL | localhost:3307 | root / 见 .env |

角色矩阵见 `docs/TEST-REPORT.md` 第二节。

## 五、微信小程序运行

1. 微信开发者工具 → 导入项目 → 目录选 `frontend/teaclub-miniprogram`，AppID 选「测试号」
2. 详情 → 本地设置 → 勾选 **不校验合法域名**（开发阶段）
3. 编译运行；登录走 mock code2session（任意字符串 code 即注册/登录）
4. Mock 支付自动完成演示，不需要商户号

> 首次运行前 `npm install && npm run build:mp-weixin`，开发者工具导入 `dist/build/mp-weixin`；
> 或直接导入源码目录（uni-app 项目开发者工具插件会处理）。

## 六、验证部署（冒烟）

```bash
cd backend/infrastructure/test
node 17-phase6-smoke.mjs           # 28 断言冒烟
node 30-full-journey.mjs           # 17 步完整业务流程
node run-regression.mjs            # 全量 14 脚本 215 断言
```

## 七、常用运维

```bash
# 重启单个服务（改代码后）
docker compose -f docker-compose.yml --env-file .env.docker up -d --build member-service
# 日志
docker logs -f teaclub-order
# 清 Redis 缓存（幂等键/登录态）
docker exec teaclub-redis redis-cli FLUSHDB
# 完整重置（删数据卷，危险！）
docker compose down -v
```

## 八、Nginx 说明（admin-web）

`deploy/web/nginx.conf`：80 端口承载 Vue 静态产物，
`/api/` 反代 `gateway-service:9000`（同源，前端无需跨域配置）。
`VITE_API_BASE_URL` 生产构建时留空即走同源。

## 九、故障排查速查

| 现象 | 排查 |
| --- | --- |
| 服务起不来 | `docker logs <name>` 看 Flyway/Nacos 报错；确认 .env 密码与 init SQL 一致 |
| 网关 502 | 目标服务未就绪，等 healthcheck；或 `docker restart teaclub-gateway` |
| 管理端登录 10201 | 权限点没刷进 token —— 重新登录；确认 deploy/sql/*.sql 已执行 |
| MQ 消息堆积 | RabbitMQ 管理台看队列；DLQ 由 DlqReplayScheduler 每 15s 自动重放 |
| 数据错乱 | 先跑 `node 12-fund-invariants.mjs` 验资金不变量 |

## 十、不包含（按需求书）

Kubernetes、生产级负载均衡、监控平台（Prometheus/Grafana）、CI/CD 流水线、
真实微信支付（商户号/证书）、库存管理 —— 均按课程设计范围裁剪。