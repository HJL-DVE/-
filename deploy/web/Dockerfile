# ============================================================
# TeaClub Web 管理后台 镜像
#
# 两阶段构建：
#   1) node 阶段：npm ci + npm run build（vue-tsc 类型检查 + vite 打包）
#   2) nginx 阶段：只把 dist 塞进 nginx，运行时零 Node 依赖
#
# 构建上下文必须是【仓库根目录】（因为要同时拿 frontend/ 与 deploy/）：
#   docker build -f deploy/web/Dockerfile -t teaclub/admin-web:1.0.0 .
#
# 推荐直接用 compose（见 backend/infrastructure/docker/docker-compose.web.yml）
# ============================================================

# ---------- 构建阶段 ----------
FROM node:22-alpine AS builder
WORKDIR /app

# 先复制依赖清单，利用 layer 缓存：源码改动不会导致重装依赖
COPY frontend/teaclub-admin/package*.json ./
RUN npm ci --no-fund --no-audit

COPY frontend/teaclub-admin/ ./
RUN npm run build

# ---------- 运行阶段 ----------
FROM nginx:1.27-alpine

RUN rm -rf /usr/share/nginx/html/* /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html
COPY deploy/web/nginx.conf /etc/nginx/conf.d/teaclub-admin.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -qO- http://127.0.0.1/healthz >/dev/null || exit 1
