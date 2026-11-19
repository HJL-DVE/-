# teaclub-admin · TeaClub 第六阶段 Web 管理后台

Vue 3 + TypeScript + Vite 5 + Element Plus 2 + Pinia 2 + Vue Router 4 + Axios 1。

## 运行

```bash
npm install
npm run dev      # http://localhost:5173
npm run build    # vue-tsc --noEmit && vite build → dist/
npm run preview
```

## 网关约定

- 所有业务请求只经网关 9000：`.env.development` 中 `VITE_API_BASE_URL=http://127.0.0.1:9000`
- **不存在硬编码服务端口 9001-9007**，`vite.config.ts` 另配 `/api` proxy 作为兜底
- 登录：admin / TeaClub@123（后端 sys_user 数据）

## 目录

```text
src/
  api/        request.ts（统一 axios 封装）+ 按域拆分的接口模块 + types.ts
  stores/     user（token/用户/动态菜单）、app（门店全局上下文）
  router/     index.ts（守卫 + 动态注册）、routes.ts（组件本地注册表）
  layout/     侧边栏（后端菜单渲染）+ 顶栏（门店切换/收银入口/用户菜单）
  views/      13 个契约页面 + 9 个「未实现」占位页
  utils/      format.ts（分↔元整数运算、状态字典）
```

## 关键实现

| 能力 | 位置 |
| --- | --- |
| 统一响应包装 / 错误码 | `src/api/request.ts`（code===0 取 data；401/4011 清 token 跳登录；403 提示；其余 ElMessage） |
| traceId 注入 | 请求头 `X-Trace-Id`，超时 10s |
| 文件上传（multipart） | `http.upload(url, file, fields)` |
| 动态菜单 | `GET /api/admin/auth/menus` → 侧边栏；路由由前端 `viewRegistry` 本地注册 |
| 门店全局上下文 | `stores/app.ts`，切换后列表/统计跟着变（`watch(currentStoreId)`） |
| 金额 | 全部整数「分」传递，`fenToYuan` / `yuanToFen` 只做字符串换算，无浮点运算 |
| 收银台金额 | 一律 `POST /api/admin/order/quote` 服务端计算，前端只展示 |
| 微信 Native | `POST /api/admin/payment/create` → `codeUrl` → `qrcode` 本地生成二维码 → 3s 轮询 `GET /api/admin/payment/orders/{paymentNo}/query` |
| 优惠券 / 积分抵扣 | 按钮置灰 + Tooltip「营销服务未上线」 |

## 后端未就绪接口（页面按「接口未就绪」如实占位，不使用假数据）

| 接口 | 实测 |
| --- | --- |
| `GET /api/admin/member/members/stats` | code=50000 |
| `GET /api/admin/member/members/by-code` | code=50000 |
| `GET /api/admin/order/stats/summary` | code=50000 |
| `GET /api/admin/wallet/accounts/member/{memberId}` | code=50000（同组的 `/accounts` 分页正常） |
| `GET /api/admin/payment/orders`、`/refunds`、`/notify-logs` | code=0 但 total=0（库里有 37/15/49 条，疑似门店/品牌过滤问题） |
