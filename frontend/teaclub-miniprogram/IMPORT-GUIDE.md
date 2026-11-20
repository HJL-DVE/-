# TeaClub 微信小程序 —— 导入微信开发者工具

## 一、导入路径（关键）

导入的是**编译产物目录**，不是工程根目录：

```text
H:\奶茶店会员与储值营销系统\frontend\teaclub-miniprogram\dist\build\mp-weixin
```

> ⚠️ 不要直接导入 `frontend/teaclub-miniprogram` 根目录 ——
> 那里是 uni-app 工程（`src/` + `vite.config.ts`），微信开发者工具不认识。
> 工具会让你选"小程序目录"，选上面那个 `dist/build/mp-weixin`。

## 二、导入步骤

1. 打开微信开发者工具 → **导入项目**
2. 目录：上面那个路径
3. AppID：
   - **有正式 AppID** → 填你的（推荐，真机预览必需）
   - **暂时没有** → 点「测试号」，工具会自动用游客模式
4. 项目名称：随便填，如 `TeaClub`
5. 后端服务：勾选 **「不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书」**
   （产物里 `project.config.json` 已设 `urlCheck: false`，但首次导入时工具可能仍提示，勾一次即可）

## 三、能不能连上后端

小程序请求的网关地址是 **`http://127.0.0.1:9000`**（`src/utils/request.ts` 全项目唯一一处）。

- 开发者工具跑在本机 → 能直接访问本机 `127.0.0.1:9000` ✅
- 需要 **11 个容器在运行**：
  ```bash
  cd backend/infrastructure/docker
  docker compose -f docker-compose.yml --env-file .env.docker up -d
  ```
- 真机预览：手机的 `127.0.0.1` 指手机自己，**访问不到你电脑**。
  真机调试需要把 `BASE_URL` 改成本机局域网 IP（如 `http://192.168.1.10:9000`），
  并确保网关容器端口对外可访问、防火墙放行。

## 四、你会看到的页面

19 个页面，tabBar 4 项（首页 / 门店 / 订单 / 我的）：

| 模块 | 页面 |
| --- | --- |
| 首页 | `pages/index/index` |
| 门店 | `pages/store/list`、`pages/store/detail` |
| 商品 | `pages/product/list`、`pages/product/detail`、`pages/cart/index` |
| 订单 | `pages/order/confirm`、`pages/order/list`、`pages/order/detail` |
| 用户 | `pages/user/login`、`pages/user/index`、`pages/user/profile` |
| 会员 | `pages/member/index`、`recharge-records`、`transactions`、`points`、`coupons` |
| 充值 | `pages/recharge/index`、`pages/recharge/result` |

## 五、登录说明（重要）

点「微信登录」走的是 `wx.login` → `POST /api/auth/wx-login`。
后端 `code2session` 是 **Mock**（无商户资质，这是既定策略）：
`openid = "mock_" + sha256(code) 前 24 位`，不访问微信服务器。

**因此登录不需要真实 AppSecret，用测试号也能登录成功。**

## 六、支付说明

- 充值/下单后走 `POST /api/payment/create`
- 无 openid 时后端自动降级 **Native**（返回 `codeUrl`），小程序用 canvas 画二维码
- Mock 渠道下「支付成功」需要后端触发 `POST /api/payment/mock/notify`，
  页面通过轮询 `GET /api/wallet/recharges/{no}` 与 `GET /api/wallet/account` 看到账
- **前端绝不会本地加余额**，余额只来自后端

## 七、断点/热更新（可选）

如果要边改边看，用开发模式：

```bash
cd frontend/teaclub-miniprogram
npm run dev:mp-weixin
```

产物在 `dist/dev/mp-weixin`，用同一个方式导入该目录，改代码会自动重编译。
（上面 `dist/build/mp-weixin` 是生产构建，改代码不会自动重编。）
