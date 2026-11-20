# TeaClub Docker 本地部署

## 一键启动

```bash
cd backend/infrastructure/docker
copy .env.example .env.docker   # Windows；Linux/Mac 为 cp .env.example .env.docker
# 编辑 .env.docker，修改所有 change_me_* 为真实值
docker compose -f docker-compose.yml --env-file .env.docker up -d --build
```

首次启动 MySQL 会自动执行：
- `mysql/init/01-create-databases.sql` 建 10 个库与业务账号
- `mysql/init/02-tc-migrator-readonly.sql` 跨库只读账号
- `mysql/init/03-report-db.sql` 报表库与跨库只读授权
- 各服务的 Flyway 迁移自动建表、种子数据（套餐/等级/优惠券模板/权限点）

## 服务端口

| 服务 | 端口 |
| --- | --- |
| Web 管理后台 | 8080 |
| Gateway | 9000 |
| auth / member / product / wallet / order / payment / marketing / report | 9001-9008 |
| MySQL | 3307（容器内 3306） |
| Redis | 6379 |
| Nacos 控制台 | 8848 |
| RabbitMQ 管理台 | 15672（vhost `/teaclub`） |

## 默认账号

管理端（`http://localhost:8080`）：

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| admin | TeaClub@123 | SUPER_ADMIN |
| hqadmin | TeaClub@123 | HQ_ADMIN |
| mgr_xihu | TeaClub@123 | STORE_MANAGER（门店1） |
| mgr_binjiang | TeaClub@123 | STORE_MANAGER（门店2） |
| cashier01 | TeaClub@123 | CASHIER |

会员端：小程序 wx-login，mock code 任意字符串即注册/登录。

## 常用命令

```bash
# 重启某个服务
docker compose -f docker-compose.yml --env-file .env.docker up -d --build member-service
# 看日志
docker logs -f teaclub-gateway
# 全量回归测试
cd ../../../backend/infrastructure/test && node run-regression.mjs
```

## 注意

- **不要提交 .env.docker**（含真实密码；仓库只保留 .env.example）
- Mock 支付生效条件：`teaclub.payment.provider=mock`（默认）
- 微信小程序：开发者工具导入 `frontend/teaclub-miniprogram`，详情勾选「不校验合法域名」