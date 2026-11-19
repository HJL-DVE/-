# TeaClub 微服务基础设施部署手册

| 项目 | 内容 |
| --- | --- |
| 文档编号 | TC-MSA-08 |
| 版本 | v1.0 |
| 编制日期 | 2026-11-19 |
| 方案 | Docker Compose 容器化 |
| 状态 | **编排已就绪，等待 WSL2 安装完成** |

---

## 1. 组件清单与端口规划

| 组件 | 镜像 | 容器端口 | 宿主机端口 | 说明 |
| --- | --- | --- | --- | --- |
| MySQL | `mysql:8.4` | 3306 | **3307** | 避开本机已占用的 3306 |
| Redis | `redis:7-alpine` | 6379 | **6380** | 避开本机已占用的 6379 |
| Nacos | `nacos/nacos-server:v2.5.1` | 8848 / 9848 | 8848 / 9848 | 注册中心 + 配置中心 |
| RabbitMQ | `rabbitmq:3.13-management-alpine` | 5672 / 15672 | 5672 / 15672 | 消息队列 + 管理台 |

**为什么端口要错开**：本机已有 MySQL 8.4（3306）与 Redis 3.0.504（6379）在运行，
单体项目仍依赖它们。容器版错开端口，两者可并存互不干扰。

---

## 2. 镜像加速（**必需前置**）

实测本机网络：

| 源 | 结果 |
| --- | --- |
| Docker Hub / registry-1.docker.io | ❌ **超时（被墙）** |
| docker.1panel.live | ✅ HTTP 200，4 个镜像全部可拉 |
| docker.m.daocloud.io | ✅ 可用 |
| 中科大 / 网易 / 腾讯云内网 | ❌ 不可用 |

**不配加速器就拉不到镜像。** 配置方法见 `deploy/DOCKER-MIRROR-SETUP.md`：

Docker Desktop → Settings → Docker Engine → 加入：

```json
{
  "registry-mirrors": [
    "https://docker.1panel.live",
    "https://docker.m.daocloud.io"
  ]
}
```

---

## 3. 文件清单

| 文件 | 作用 |
| --- | --- |
| `deploy/docker-compose.infrastructure.yml` | 4 个中间件的编排定义 |
| `deploy/.env.docker` | 编排用环境变量（密码，已 gitignore） |
| `deploy/mysql/init/01-create-databases.sql` | 首次启动自动建 9 库 + 10 账号 |
| `deploy/DOCKER-MIRROR-SETUP.md` | 镜像加速配置指引 |

---

## 4. 数据库与账号隔离设计

容器首次启动时自动执行 `01-create-databases.sql`，建立：

### 4.1 九个库

```text
iam_db  member_db  product_db  wallet_db  order_db
payment_db  marketing_db  inventory_db  report_db
```

### 4.2 十个账号（每个只能访问自己的库）

| 账号 | 可访问库 | 密码 |
| --- | --- | --- |
| `tc_auth` | `iam_db` | `TcAuth_2026` |
| `tc_member` | `member_db` | `TcMember_2026` |
| `tc_product` | `product_db` | `TcProduct_2026` |
| `tc_wallet` | `wallet_db` | `TcWallet_2026` |
| `tc_order` | `order_db` | `TcOrder_2026` |
| `tc_payment` | `payment_db` | `TcPayment_2026` |
| `tc_marketing` | `marketing_db` | `TcMarketing_2026` |
| `tc_inventory` | `inventory_db` | `TcInventory_2026` |
| `tc_report` | `report_db` | `TcReport_2026` |
| `tc_migrator` | 6 个业务库（**仅迁移期使用**） | `TcMigrator_2026` |

> 密码仅用于本地开发环境。生产必须通过密钥管理注入。

### 4.3 隔离验证方法

```bash
# 用 tc_wallet 登录，尝试访问 order_db —— 必须被拒绝
docker exec -it teaclub-mysql mysql -utc_wallet -pTcWallet_2026 -e "SELECT COUNT(*) FROM order_db.oms_order;"
# 期望：ERROR 1142 (42000): SELECT command denied
```

**这一步必须实际执行并确认被拒绝**，不能假设。

---

## 5. 启动与验证

### 5.1 启动

```bash
cd "H:\奶茶店会员与储值营销系统"

docker compose -f deploy/docker-compose.infrastructure.yml \
  --env-file deploy/.env.docker up -d

# 观察启动进度
docker compose -f deploy/docker-compose.infrastructure.yml ps
```

### 5.2 逐项验证

```bash
# 1) 容器状态（4 个都应是 healthy）
docker ps --filter "name=teaclub-" --format "table {{.Names}}\t{{.Status}}"

# 2) MySQL
docker exec teaclub-mysql mysql -uroot -pChangeMe_Root_2026 -e "SHOW DATABASES LIKE '%_db';"

# 3) Redis
docker exec teaclub-redis redis-cli PING        # 期望 PONG

# 4) Nacos（等待约 60 秒后）
curl http://localhost:8848/nacos/v1/console/health/readiness

# 5) RabbitMQ 管理台
#    浏览器打开 http://localhost:15672  (teaclub / TeaClub_MQ_2026)
```

### 5.3 权限隔离验证（关键）

```bash
# 应被拒绝
docker exec teaclub-mysql mysql -utc_wallet -pTcWallet_2026 \
  -e "SELECT COUNT(*) FROM order_db.oms_order;"

# 应成功
docker exec teaclub-mysql mysql -utc_wallet -pTcWallet_2026 \
  -e "SELECT COUNT(*) FROM wallet_db.wal_wallet;"
```

---

## 6. 当前阻塞状态

| 项 | 状态 | 说明 |
| --- | --- | --- |
| 编排文件 | ✅ 已就绪 | `docker compose config` 语法校验通过 |
| 镜像可达性 | ✅ 已验证 | 4 个镜像在加速源上均返回 HTTP 200 |
| 建库脚本 | ✅ 已就绪 | 9 库 + 10 账号 |
| **WSL2** | ⏳ **安装中** | 你正在执行 `wsl --install` |
| **Docker 守护进程** | ❌ 未就绪 | 依赖 WSL2 |

**WSL 装完并重启后，即可执行 §5 的启动流程。**

---

## 7. 与单体项目的关系

**当前单体应用（`backend/`，端口 8080）继续使用本机 MySQL 3306 + Redis 6379，不受影响。**

容器环境是**并行的一套**，用于微服务迁移：

```text
本机环境（继续使用）          容器环境（新增）
  MySQL  3306  ← 单体应用        MySQL   3307  ← 微服务
  Redis  6379  ← 单体应用        Redis   6380  ← 微服务
                                 Nacos   8848
                                 RabbitMQ 5672/15672
```

两套互不干扰，可随时回退。
