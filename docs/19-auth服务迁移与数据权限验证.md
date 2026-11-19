# TeaClub 第五阶段·任务三：auth-service 业务迁移与数据权限验证

| 项目 | 内容 |
| --- | --- |
| 文档编号 | TC-MSA-13 |
| 日期 | 2026-11-19 |
| 范围 | auth-service 业务迁移（58 个文件）+ 数据权限端到端验证 + 目录结构调整 |
| 状态 | 登录 / 角色 / 权限 / 门店范围全部实测通过 |

---

## 1. 目录结构调整

按要求以 `backend/` 为微服务父目录：

```text
backend/                          <- 微服务父目录
├── src/  pom.xml                 <- 现有单体（保留，未改动）
├── teaclub-cloud/                <- 微服务工程（16 模块）
├── teaclub-admin/                <- 管理后台前端
├── teaclub-miniprogram/          <- 小程序前端
└── infrastructure/
    ├── docker/                   <- Compose 编排
    └── migration/                <- 数据迁移 + 备份
```

根目录保留：`docs/`、`scripts/`、`CHANGELOG.md`、`README.md`。

**同步修正的引用**：

| 文件 | 修改 |
| --- | --- |
| `docker-compose.yml` | build context `../teaclub-cloud` → `../../teaclub-cloud` |
| `migrate.js` / `verify.js` | ROOT 常量改为 `__dirname + '/../../..'`（不再硬编码绝对路径） |
| `.gitignore` | `deploy/.env.docker` → `backend/infrastructure/docker/.env.docker` |

---

## 2. auth-service 迁移清单（58 个文件）

| 类型 | 数量 | 内容 |
| --- | --- | --- |
| 实体 | 9 | SysUser, SysRole, SysPermission, SysStore, SysBrand, SysUserStore, SysUserRole, SysRolePermission, SysOperationLog |
| DTO | 13 | LoginRequest, UserCreateRequest, UserUpdateRequest, UserQuery, RoleSaveRequest, PermissionSaveRequest, StoreSaveRequest, StoreQuery, BrandUpdateRequest, GrantStoresRequest, ChangePasswordRequest, ResetPasswordRequest, StatusUpdateRequest |
| VO | 9 | LoginVO, UserInfoVO, UserVO, RoleVO, PermissionVO, MenuVO, StoreVO, StoreBriefVO, BrandVO |
| Mapper | 9 | 含 SysRoleMapper.selectByUserId / SysPermissionMapper.selectByUserId（数据权限核心） |
| Service | 12 | 接口 6 + 实现 6 |
| Controller | 6 | Auth, SysUser, SysRole, SysPermission, SysStore, SysBrand |
| 特有 | 1 | `security/TokenPrincipalResolver`（角色/权限/门店范围实时装载） |

**包名与分层**：`com.teaclub.system.*` → `com.teaclub.cloud.auth.*`，按 entity/dto/vo/mapper/service/controller 分包。

---

## 3. 数据权限实测（核心验收）

### 3.1 登录成功，迁移后的 BCrypt 密码仍可校验

```text
账号          密码          结果
admin         TeaClub@123   登录成功，角色 SUPER_ADMIN，权限点 68 个
hqadmin       TeaClub@123   登录成功
mgr_xihu      TeaClub@123   登录成功，角色 STORE_MANAGER，权限点 28 个
cashier01     TeaClub@123   登录成功，角色 CASHIER，权限点 15 个

错误密码      -> 10104 用户名或密码错误
不存在账号    -> 10104 用户名或密码错误
```

> 密码散列原样迁移（`$2a$10$GQ8LFacPDaAkj...`），BCrypt 校验通过，
> **说明历史数据可继续使用，无需重置密码**。

### 3.2 门店数据范围

```text
账号          userType   allStoreAccess   可见门店
admin         HQ         true             全部 3 家（西湖/滨江/城西银泰）
mgr_xihu      STORE      false            仅 1 家（西湖旗舰店）
cashier01     STORE      false            仅 1 家（西湖旗舰店）
```

### 3.3 越权拦截

```text
店长 GET  /api/admin/store/stores/999
  -> 10202 "没有门店[999]的数据权限"          数据权限拦截

店长 DELETE /api/admin/store/stores/2
  -> 10201 "没有该操作权限"                  功能权限拦截

收银员 DELETE /api/admin/system/users/1
  -> 10201 "没有该操作权限"                  功能权限拦截

无 token GET /api/admin/system/users
  -> 401 10101 "未登录或登录已失效"           认证拦截
```

**三类拦截全部生效**：认证（401）、功能权限（403）、数据范围（403）。

### 3.4 经 Gateway 的完整链路

```text
1. POST /api/admin/auth/login（白名单放行）
   -> 200，token 344 字符
2. GET /api/admin/auth/me（携带 token）
   -> 200，用户 admin，可见 3 家门店
3. GET /api/admin/store/stores
   -> 200，可见 3 家门店
```

---

## 4. API 兼容性

单体与微服务版接口路径**完全一致**：

```text
单体 24 个接口 -> 微服务 24 个全在，0 缺失
微服务额外 4 个：/api/auth/internal/**、/api/auth/feign/**、/api/auth/dev/**
```

前端无需任何改动即可切换。

---

## 5. 关键实现说明

### 5.1 权限上下文的传递链

```text
auth-service 登录
  -> TokenPrincipalResolver 实时查库装载 角色/权限点/门店范围
  -> 写入 JWT claims（roles / permissions / allStoreAccess / storeIds）
  -> Gateway 解析 JWT，用请求头透传（X-Roles / X-Permissions / X-Store-Ids）
  -> 各服务的 JwtAuthenticationFilter 据此构建 Authentication
  -> @PreAuthorize("hasAuthority('store:list')") 校验
```

> **与单体的差异**：单体由 `TokenPrincipalResolver` 在每个服务内实时查库；
> 微服务中 iam_db 只归 auth-service，其它服务不得跨库查询，
> 因此权限上下文由 Gateway 统一解析并透传。

### 5.2 新增的公共组件

| 组件 | 作用 |
| --- | --- |
| `JwtAuthenticationFilter` | 从请求头构建 Authentication，供 @PreAuthorize 使用 |
| `LoginProperties` | 登录失败锁定策略（最大失败次数、锁定分钟） |
| `PasswordEncoder` Bean | BCrypt，保证历史散列可校验 |
| `MyMetaObjectHandler` | createTime/updateTime 自动填充 |
| `AuditLog` 注解 | 从单体 common/annotation 迁移 |

### 5.3 错误码补齐

单体 37 个错误码，微服务版原仅 26 个。已补齐全部业务码（编号与消息与单体一致）：

```text
20001-20007  用户/角色/权限
30001-30003  品牌/门店
40001-40005  会员
50001-50009  商品
10301-10304  网关
10401-10406  Feign/内部调用
10501-10503  消息
```

共 50 个，无编号冲突。

---

## 6. 本轮修复的问题

| # | 问题 | 根因 | 修复 |
| --- | --- | --- | --- |
| 1 | 迁移脚本跨实例失败 | 3306/3307 不同实例 | 改 `mysqldump \| mysql` 管道 |
| 2 | 包名重复 `dto;dto;` | 正则替换两次 | 统一修正 40 个文件 |
| 3 | `import import` | 同上 | 修正 |
| 4 | Mapper import 变成 `auth.mapper.entity` | 多段正则叠加 | 修正 14 个文件 |
| 5 | `common.annotation` 未迁移 | 遗漏 | 迁到 common-web/annotation |
| 6 | 缺 `ACCOUNT_LOCKED` | 微服务版遗漏 | 补齐（后连同 24 个业务码一起补） |
| 7 | 缺 `PasswordEncoder` Bean | 单体在 SecurityConfig 里 | 迁到 common-security 自动配置 |
| 8 | 缺 `LoginProperties` Bean | 在 common 包不在扫描范围 | 去掉 @Component，改自动配置注册 |
| 9 | **所有管理接口 500** | **缺 `JwtAuthenticationFilter`，@PreAuthorize 无权限可判** | **迁移该过滤器 + Gateway 透传 X-Permissions** |
| 10 | 权限拒绝返回 500 | `AuthorizationDeniedException` 落到通用处理器 | 加专门处理器返回 403 |
| 11 | Gateway 白名单缺 `/api/admin/auth/login` | 单体迁移后路径变了 | 补白名单（含注册路径） |
| 12 | 载荷缺 permissions claim | JWT 未写入 | 补 `.claim("permissions", ...)` |

> **第 9 项是本轮最重要的发现**：权限校验看似"配置齐全"，
> 但因为没有把权限装进 Spring Security 的 Authentication，
> 所有 `@PreAuthorize` 都会拒绝 —— 表现为 500 而非 403，
> 极易被误判为业务 bug。

---

## 7. 未完成项（如实标注）

| # | 项 | 原因 |
| --- | --- | --- |
| 1 | auth-service 单元测试 | 本轮只做端到端验证 |
| 2 | member / product / order / payment 服务迁移 | 未开始 |
| 3 | Outbox / Inbox + 8 类事件发布 | 未实现 |
| 4 | 微信支付 v3 验签与退款 | 未开始 |
| 5 | 部分金额冻结能力 | 源表仅有 status 字段 |
| 6 | 令牌撤销（黑名单） | 单体有 `auth:revoke:` 缓存键，微服务版未实现 |

> **不能声称完成**：以上 6 项均未实际验证。

---

## 8. 下一批计划

1. **member-service 迁移**（会员注册/资料/微信绑定/可见口径 B）
2. **product-service 迁移**（分类/SPU/SKU/门店商品/价格）
3. auth-service 单元测试补齐
4. Outbox/Inbox 事件机制
5. order / payment 迁移（含微信支付 v3）
