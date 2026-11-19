-- ============================================================
-- auth-service V1: 由单体 backend 的 V1__init_system.sql 迁移而来
--
-- 说明：表结构原样保留，保证历史数据可直接搬入且 ID 不变。
-- 仅调整数据库归属（单体 teaclub 库 -> auth-service 独立库）。
-- ============================================================

-- ============================================================
-- V1: 系统管理模块（品牌 / 门店 / 账号 / 角色 / 权限 / 配置 / 日志）
-- ============================================================

CREATE TABLE sys_brand (
  id                        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  brand_code                VARCHAR(32)   NOT NULL COMMENT '品牌编码',
  brand_name                VARCHAR(64)   NOT NULL COMMENT '品牌名称',
  logo_url                  VARCHAR(512)  NULL     COMMENT '品牌 Logo',
  contact_phone             VARCHAR(32)   NULL     COMMENT '客服电话',
  wallet_cross_store_enabled TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '储值是否允许跨店消费',
  wallet_deduct_order       VARCHAR(16)   NOT NULL DEFAULT 'GIFT_FIRST' COMMENT '扣款顺序',
  gift_expire_days          INT           NOT NULL DEFAULT 365 COMMENT '赠送金有效天数',
  status                    VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  create_time               DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time               DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted                   TINYINT(1)    NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_brand_code (brand_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='品牌';

CREATE TABLE sys_store (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id       BIGINT UNSIGNED NOT NULL COMMENT '所属品牌',
  store_code     VARCHAR(32)   NOT NULL COMMENT '门店编码',
  store_name     VARCHAR(64)   NOT NULL COMMENT '门店名称',
  province       VARCHAR(32)   NULL,
  city           VARCHAR(32)   NULL,
  district       VARCHAR(32)   NULL,
  address        VARCHAR(255)  NOT NULL COMMENT '详细地址',
  longitude      DECIMAL(10,6) NULL COMMENT '经度',
  latitude       DECIMAL(10,6) NULL COMMENT '纬度',
  phone          VARCHAR(32)   NULL COMMENT '门店电话',
  manager_name   VARCHAR(32)   NULL COMMENT '店长姓名',
  business_hours VARCHAR(64)   NULL COMMENT '营业时间',
  open_date      DATE          NULL COMMENT '开业日期',
  status         VARCHAR(16)   NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/SUSPENDED/CLOSED',
  remark         VARCHAR(255)  NULL,
  create_time    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted        TINYINT(1)    NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_store_code (brand_id, store_code),
  KEY idx_store_brand_status (brand_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门店';

CREATE TABLE sys_user (
  id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id             BIGINT UNSIGNED NOT NULL,
  username             VARCHAR(64)   NOT NULL COMMENT '登录账号',
  password             VARCHAR(100)  NOT NULL COMMENT 'BCrypt 密文',
  real_name            VARCHAR(32)   NOT NULL COMMENT '真实姓名',
  phone                VARCHAR(20)   NULL,
  email                VARCHAR(128)  NULL,
  avatar_url           VARCHAR(512)  NULL,
  user_type            VARCHAR(16)   NOT NULL DEFAULT 'STORE' COMMENT 'HQ 总部 / STORE 门店',
  status               VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED/LOCKED',
  login_fail_count     INT           NOT NULL DEFAULT 0,
  lock_until           DATETIME(3)   NULL,
  last_login_time      DATETIME(3)   NULL,
  last_login_ip        VARCHAR(64)   NULL,
  password_update_time DATETIME(3)   NULL,
  create_time          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted              TINYINT(1)    NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (brand_id, username),
  KEY idx_user_phone (brand_id, phone),
  KEY idx_user_type_status (brand_id, user_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='后台账号';

CREATE TABLE sys_role (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id    BIGINT UNSIGNED NOT NULL,
  role_code   VARCHAR(64)   NOT NULL COMMENT '角色编码',
  role_name   VARCHAR(64)   NOT NULL,
  data_scope  VARCHAR(16)   NOT NULL DEFAULT 'STORE' COMMENT 'ALL 全品牌 / STORE 授权门店',
  is_builtin  TINYINT(1)    NOT NULL DEFAULT 0,
  sort        INT           NOT NULL DEFAULT 0,
  status      VARCHAR(16)   NOT NULL DEFAULT 'ENABLED',
  remark      VARCHAR(255)  NULL,
  create_time DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted     TINYINT(1)    NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code (brand_id, role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色';

CREATE TABLE sys_permission (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  parent_id   BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父级，0 为根',
  perm_code   VARCHAR(96)   NOT NULL COMMENT '权限标识，如 store:order:list',
  perm_name   VARCHAR(64)   NOT NULL,
  perm_type   VARCHAR(16)   NOT NULL COMMENT 'MENU 菜单 / BUTTON 按钮 / API 接口',
  route_path  VARCHAR(128)  NULL COMMENT '前端路由路径',
  component   VARCHAR(128)  NULL COMMENT '前端组件路径',
  icon        VARCHAR(64)   NULL,
  sort        INT           NOT NULL DEFAULT 0,
  visible     TINYINT(1)    NOT NULL DEFAULT 1,
  status      VARCHAR(16)   NOT NULL DEFAULT 'ENABLED',
  create_time DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted     TINYINT(1)    NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_perm_code (perm_code),
  KEY idx_perm_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限点';

CREATE TABLE sys_user_role (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id     BIGINT UNSIGNED NOT NULL,
  role_id     BIGINT UNSIGNED NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id),
  KEY idx_ur_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联';

CREATE TABLE sys_role_permission (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  role_id       BIGINT UNSIGNED NOT NULL,
  permission_id BIGINT UNSIGNED NOT NULL,
  create_time   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_perm (role_id, permission_id),
  KEY idx_rp_perm (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联';

CREATE TABLE sys_user_store (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id     BIGINT UNSIGNED NOT NULL,
  store_id    BIGINT UNSIGNED NOT NULL,
  is_default  TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否默认门店',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_store (user_id, store_id),
  KEY idx_us_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户门店授权（数据权限核心）';

CREATE TABLE sys_config (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id     BIGINT UNSIGNED NOT NULL,
  config_key   VARCHAR(96)   NOT NULL,
  config_value VARCHAR(1024) NOT NULL,
  value_type   VARCHAR(16)   NOT NULL DEFAULT 'STRING',
  remark       VARCHAR(255)  NULL,
  create_time  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_config_key (brand_id, config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='品牌配置';

CREATE TABLE sys_operation_log (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id       BIGINT UNSIGNED NOT NULL,
  operator_id    BIGINT UNSIGNED NULL,
  operator_name  VARCHAR(64)   NULL,
  store_id       BIGINT UNSIGNED NULL,
  module         VARCHAR(64)   NOT NULL,
  operation      VARCHAR(128)  NOT NULL,
  request_uri    VARCHAR(255)  NULL,
  request_method VARCHAR(16)   NULL,
  request_param  TEXT          NULL,
  response_code  INT           NULL,
  success        TINYINT(1)    NOT NULL DEFAULT 1,
  error_msg      VARCHAR(1024) NULL,
  ip             VARCHAR(64)   NULL,
  user_agent     VARCHAR(255)  NULL,
  trace_id       VARCHAR(64)   NULL,
  cost_ms        INT           NULL,
  create_time    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_oplog_brand_time (brand_id, create_time),
  KEY idx_oplog_operator (operator_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志（禁止删除）';

CREATE TABLE sys_idempotency_record (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  idem_key      VARCHAR(128) NOT NULL,
  biz_type      VARCHAR(64)  NOT NULL,
  request_hash  VARCHAR(64)  NULL,
  response_body TEXT         NULL,
  expire_time   DATETIME(3)  NOT NULL,
  create_time   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_idem_key (idem_key),
  KEY idx_idem_expire (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='接口幂等记录';
