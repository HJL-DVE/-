# TeaClub 基础设施

本目录承载容器化部署编排。

## 目录结构

```text
infrastructure/
├── docker/                 Docker Compose 编排
│   ├── docker-compose.yml  完整编排（4 中间件 + 11 应用服务）
│   ├── .env.docker         环境变量（含密码，已 gitignore）
│   ├── mysql/init/         9 库 + 10 账号初始化脚本
│   └── nacos/conf/         Nacos 修正配置（内嵌 Derby）
└── scripts/                运维脚本
```

## 常用命令

```bash
cd infrastructure/docker

# 启动（首次构建镜像）
docker compose -f docker-compose.yml --env-file .env.docker up -d --build

# 查看状态
docker compose -f docker-compose.yml --env-file .env.docker ps

# 查看日志
docker logs teaclub-gateway -f

# 停止
docker compose -f docker-compose.yml --env-file .env.docker down

# 停止并清空数据
docker compose -f docker-compose.yml --env-file .env.docker down -v
```

> `.env.docker` 含真实密码与密钥，**禁止提交**，已在 `.gitignore` 中排除。

相关文档：`docs/17-容器化部署完整验证报告.md`
