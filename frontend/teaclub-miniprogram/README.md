# TeaClub 微信小程序（uni-app + Vue 3 + TypeScript）

契约依据：`docs/33-第六阶段接口契约与页面清单.md` 第三、六节。
所有业务请求经网关 `http://127.0.0.1:9000`，**不存在**硬编码微服务端口。

## 环境

- Node v22.23.1 / npm 10.9.8（registry: https://registry.npmmirror.com）
- uni-app 3.x（`@dcloudio/*` 统一版本 `3.0.0-alpha-5030720261117001`）、vue 3、pinia 2、vite 5、typescript 5

## 命令

```bash
npm install                # 已执行，571 packages
npm run dev:mp-weixin      # 开发（watch）
npm run build:mp-weixin    # 生产构建 → dist/build/mp-weixin
```

导入微信开发者工具：打开 `dist/build/mp-weixin`。
`manifest.json` 已设置 `mp-weixin.setting.urlCheck=false`，构建产出的 `project.config.json` 中 `urlCheck` 为 `false`（等价于勾选「不校验合法域名」）。
未配置真实 appid 时，开发者工具使用 `touristappid` 测试号即可；正式联调请在 `src/manifest.json` 的 `mp-weixin.appid` 填入。

## 目录

```
src/
├── pages.json          # 契约第六节全部页面 + tabBar（首页/门店/订单/我的）
├── manifest.json       # mp-weixin 配置（urlCheck:false）
├── utils/
│   ├── request.ts      # 统一请求封装：Bearer 注入、code!==0 toast、401/4011 清 token 跳登录
│   ├── auth.ts         # token / 会员信息本地存储
│   ├── money.ts        # fen2yuan / yuan2fen（整数分运算，无浮点）
│   └── normalize.ts    # 契约字段与后端 VO 字段兼容取值、specJson 解析
├── api/                # 按契约第三节逐一对应，无自造接口
├── stores/             # pinia：auth / store（门店上下文）/ cart（购物车+规格）
└── pages/              # 契约第六节 18 个页面 + 购物车页
```

## 关键约束（已实现）

- 响应包装 `{ code, message, data, traceId, timestamp }`，`code===0` 为成功。
- 金额一律「分」（整数），展示用 `fen2yuan` 做整除取余；购物车合计用整数求和。
- 分页入参 `pageNo/pageSize`，出参 `{ records, total, pageNo, pageSize, totalPages }`。
- 微信登录：`wx.login` 取 `code` → `POST /api/auth/wx-login`，**token 完全由后端签发，前端不伪造**。
- 下单金额**必须**经 `POST /api/order/quote`，确认订单页只展示试算结果。
- 充值/支付成功后**绝不**在本地加余额：`pages/recharge/result` 轮询
  `GET /api/wallet/recharges/{rechargeNo}` 与 `GET /api/wallet/account`，余额只取后端返回。
- 积分 / 优惠券：`pages/member/points`、`pages/member/coupons` 为「敬请期待」预留页，无接口、无假数据。
- 糖度 / 温度 / 加料存 pinia 购物车；下单请求体为 `OrderItemRequest { skuId, quantity }`
  （后端该 DTO 无 remark 字段），规格文案汇总写入订单级 `remark`。

## 接口自检脚本

```bash
node scripts/probe-gateway.mjs     # 直连网关，逐条打印真实 http/code/message
node scripts/probe-with-admin.mjs  # 带管理端 token 越过网关鉴权，观察下游服务真实响应
```

两个脚本只用 `fetch`，不含任何 mock 数据。最近一次结果见 `probe-result.json`（gitignore 中忽略）。
