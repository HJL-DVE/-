# TeaClub 环境变更记录：Redis 与 Docker

| 项目 | 内容 |
| --- | --- |
| 文档编号 | TC-DOC-15 |
| 日期 | 2026-11-19 |
| 变更类型 | 本机开发环境补装 |
| 结果 | Redis ✅ 已安装并接入；Docker ⚠️ 已安装但无法启动 |

---

## 1. Redis（已完成）

### 1.1 安装结果

| 项 | 值 |
| --- | --- |
| 安装方式 | `winget install --id Redis.Redis` |
| 发行版 | Redis on Windows（tporadowski 移植版） |
| 版本 | 3.0.504 |
| 安装路径 | `C:\Program Files\Redis` |
| Windows 服务名 | `Redis`（Running / Automatic） |
| 端口 | 6379 |
| 连通性验证 | `redis-cli PING` → **PONG** |

> **为什么不是官方 Redis**：官方 Redis 自 6.x 起不再支持原生 Windows；
> 本机无 Docker（见 §2），故选用了 Windows 原生移植版，可直接作为 Windows 服务运行。
> 如需 Redis 7 特性，待 Docker 可用后改用容器版 `redis:7-alpine`。

### 1.2 应用接入

配置已从 `local` 切换到 `redis`：

```bash
# .env
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_PASSWORD=
TEACLUB_CACHE_PROVIDER=redis
```

`CacheService` 现在注入 `RedisCacheService`（`ConditionalOnProperty` 生效）。

健康检查已重新启用 Redis 探针：

```yaml
management:
  health:
    redis:
      enabled: true
```

**实测 `/actuator/health` 输出**：

```json
{
  "status": "UP",
  "components": {
    "db":    { "status": "UP" },
    "ping":  { "status": "UP" },
    "redis": { "status": "UP" },
    "ssl":   { "status": "UP" }
  }
}
```

### 1.3 回归验证

切换缓存实现后重跑全部测试套件：

| 套件 | 结果 |
| --- | --- |
| 基础业务 | ✅ 99 / 99 |
| 越权隔离 | ✅ 19 / 19 |
| 资金业务 | ✅ 59 / 59 |
| 并发幂等 | ✅ 21 / 21 |

**切换缓存后端未破坏任何功能。**

---

## 2. Docker（已安装，但**无法启动**）

### 2.1 安装结果

| 项 | 值 |
| --- | --- |
| 安装方式 | `winget install --id Docker.DockerDesktop` |
| 版本 | Docker Desktop 4.99.0（引擎 CLI 29.9.3） |
| 安装路径 | `C:\Program Files\Docker\Docker` |
| 安装结果 | ✅ 成功（EXIT=0） |
| **守护进程** | ❌ **无法启动** |

### 2.2 阻塞原因（实测）

| 检查项 | 实测值 | 结论 |
| --- | --- | --- |
| 当前进程是否管理员 | `IsAdmin: False` | 无法启用 Windows 可选功能 |
| WSL 是否安装 | `wsl --status` → "尚未安装适用于 Linux 的 Windows 子系统" | **缺失** |
| WSL 发行版 | `wsl -l -v` → 无 | **缺失** |
| HypervisorPresent | `False` | 虚拟化平台未启用 |
| VirtualizationFirmwareEnabled | `True` | BIOS 层虚拟化已开启（硬件没问题） |

**错误信息**：

```text
failed to connect to the docker API at npipe:////./pipe/docker_engine
request returned 500 Internal Server Error for API route
http://%2F%2F.%2Fpipe%2FdockerDesktopLinuxEngine/v1.56/info
```

**根本原因**：Docker Desktop 依赖 WSL2 作为后端，而 WSL2 需要在
**管理员权限**下启用 `Microsoft-Windows-Subsystem-Linux` 与 `VirtualMachinePlatform`
两个 Windows 可选功能，并重启系统。

当前执行环境**不是管理员**，无法完成这一步。

### 2.3 需要你手动执行的步骤

请以**管理员身份**打开 PowerShell，依次执行：

```powershell
# 1. 启用 WSL 与虚拟机平台（会要求重启）
wsl --install --no-distribution

# 2. 重启电脑后，设置 WSL 默认版本为 2
wsl --set-default-version 2

# 3. 启动 Docker Desktop（开始菜单搜索 "Docker Desktop"）
#    首次启动会要求接受协议

# 4. 验证
docker --version
docker run --rm hello-world
```

完成后告诉我，我会：

1. 把 `deploy/docker-compose.yml` 写出来并真实拉起
2. 用容器版 MySQL 8.4 + Redis 7 + 后端 + Nginx 验证一键部署
3. 更新部署文档

### 2.4 在此之前项目的运行状态

**项目不依赖 Docker 运行**，当前环境已可完整开发与验证：

| 能力 | 状态 |
| --- | --- |
| 后端启动 | ✅ 本机 JDK + Maven |
| 数据库 | ✅ 本机 MySQL 8.4（`teaclub` 库，Flyway 到 V8） |
| 缓存 | ✅ 本机 Redis 3.0.504 |
| 全部测试 | ✅ 198 项接口断言 + 44 项单元测试 |
| Docker 一键部署 | ❌ 待 WSL 就绪 |

---

## 3. 微信小程序凭据（已配置）

已从提供的文件中读取并写入**本地 `.env`（已被 .gitignore 忽略）**，文档中不记录明文：

```bash
WX_MINI_APP_ID=<已配置，不在此文档记录>
WX_MINI_APP_SECRET=<已配置，不在此文档中明文记录>
```

`application.yml` 通过环境变量引用，**仓库内不出现明文密钥**：

```yaml
teaclub:
  wechat:
    mini-app-id: ${WX_MINI_APP_ID:}
    mini-app-secret: ${WX_MINI_APP_SECRET:}
```

**泄露扫描结果**：除 `.env` 外，全仓库无 AppID/AppSecret 明文（已脚本验证）。

> ⚠️ 安全提醒：该 AppSecret 曾在对话中传输。建议在微信公众平台
> 「开发 → 开发管理 → 开发设置」中**重置一次 AppSecret**，然后更新 `.env`。

---

## 4. 本次变更涉及的文件

| 文件 | 变更 |
| --- | --- |
| `.env` | 切换 `TEACLUB_CACHE_PROVIDER=redis`；写入小程序 AppID/Secret |
| `.env.example` | 补充小程序变量说明 |
| `backend/src/main/resources/application.yml` | 启用 Redis 健康探针；新增 `teaclub.wechat` 配置项 |
| `docs/15-环境变更记录-Redis与Docker.md` | 本文档 |
| `docs/08-环境预研与部署规划.md` | 需同步更新 P3/P2 状态（见下） |

---

## 5. 已知限制（更新）

| # | 项 | 状态 |
| --- | --- | --- |
| P2 | Docker 不可用 | ⚠️ 已安装，**待管理员启用 WSL 并重启** |
| P3 | Redis 不可用 | ✅ **已解决** |
| P4 | MySQL root 密码未知 | ✅ 已解决 |
| P1 | `mvn` 命令被劫持 | ⚠️ 仍存在，统一使用 `apache-maven-3.9.6\bin\mvn.cmd` |
