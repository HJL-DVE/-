# Docker 镜像加速配置

## 为什么需要

实测本机网络：

| 镜像源 | 可达性 |
| --- | --- |
| Docker Hub (hub.docker.com) | ❌ 超时 |
| registry-1.docker.io | ❌ 超时 |
| **docker.1panel.live** | ✅ **HTTP 200，manifest 可取** |
| **docker.m.daocloud.io** | ✅ 可用 |
| 阿里云个人镜像 | ⚠️ 需登录 |

**不配置镜像加速器将无法拉取任何镜像。**

---

## 配置步骤

1. 打开 Docker Desktop → Settings → Docker Engine

2. 将内容替换为：

```json
{
  "builder": { "gc": { "defaultKeepStorage": "20GB", "enabled": true } },
  "experimental": false,
  "registry-mirrors": [
    "https://docker.1panel.live",
    "https://docker.m.daocloud.io"
  ],
  "features": { "buildkit": true }
}
```

3. 点击 **Apply & Restart**

4. 验证：

```bash
docker info | grep -A2 "Registry Mirrors"
docker pull hello-world
```

---

## 配置文件位置

Windows Docker Desktop 的 daemon 配置存储在：

```
%USERPROFILE%\.docker\daemon.json
```

也可通过 GUI 修改，两者等效。

---

## 若加速器仍不可用

备选方案（按优先级）：

1. 换用其他公共加速器
2. 使用阿里云容器镜像服务的个人专属加速地址（需注册）
3. 在能访问外网的机器上 `docker save` 后拷贝镜像 `docker load`
4. Nacos 改用官方 zip 包本地运行（不依赖 Docker）
