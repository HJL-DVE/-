# TeaClub Git 凭据配置说明

| 项目 | 内容 |
| --- | --- |
| 文档编号 | TC-DOC-16 |
| 仓库 | https://github.com/HJL-DVE/- |
| 凭据机制 | Git Credential Manager 2.9.1 + Windows 凭据管理器 |

---

## 1. 当前认证状态（实测）

```text
git config credential.helper          → manager（全局已配置，来源：Git for Windows 安装时写入）
Windows 凭据管理器 github 条目         → 存在
  LegacyGeneric:target=git:https://github.com
  LegacyGeneric:target=GitHub - https://api.github.com/HJL-DVE
git ls-remote origin（无交互）         → exit=0 ✅
```

**结论：认证已持久化，日常 `git push` 无需再输入账号密码或 Token。**

---

## 2. 上次推送为什么"看起来需要认证"

上次执行的是：

```bash
git -c credential.helper= -c core.askpass= push -u origin main
```

`-c credential.helper=` **显式清空**了凭据助手，导致 Git 无法读取已存储的凭据，
于是报 `could not read Username for 'https://github.com'`。

这是排查时的临时手段，**不是环境问题**。第二次去掉该参数后立即推送成功。

---

## 3. 日常使用

```bash
cd "H:\奶茶店会员与储值营销系统"
git add -A
git commit -m "feat: xxx"
git push          # 直接可用
```

**不要**再加 `-c credential.helper=`，那会导致认证失败。

---

## 4. 若未来凭据失效

### 情形 A：Token 过期或被撤销

```powershell
# 清除旧凭据
cmdkey /delete:git:https://github.com

# 重新推送，会弹出 GCM 登录窗口
git push

# 或用 Personal Access Token 直接写入
git config --global credential.helper manager
```

GCM 会弹出浏览器登录窗口，完成 GitHub OAuth 后自动存储。

### 情形 B：改用 Personal Access Token（无浏览器环境）

```powershell
# 生成 PAT：GitHub → Settings → Developer settings → Personal access tokens
# 权限至少勾选 repo

# 方式一：直接写入凭据管理器
cmdkey /generic:git:https://github.com /user:HJL-DVE /pass:<你的PAT>

# 方式二：用 store 助手（明文存 ~/.git-credentials，不推荐）
git config --global credential.helper store
git push        # 输入一次，之后明文保存
```

> ⚠️ 方式二把 Token 明文存在 `~/.git-credentials`，**不建议**在多用户机器使用。

---

## 5. 安全提醒

| 项 | 说明 |
| --- | --- |
| 凭据位置 | Windows 凭据管理器（加密存储），非明文文件 |
| 仓库中是否有密钥 | ❌ 无。已用 `git grep` 验证提交树中无 DB 密码 / AppSecret |
| `.env` 是否提交 | ❌ 未提交，已被 `.gitignore` 忽略 |
| 数据库密码 | 已改为环境变量 `${MYSQL_PASSWORD}` 注入 |

