-- ============================================================
-- wallet-service V1: 由单体 backend 的 V5__init_wallet.sql 迁移而来
--
-- 说明：表结构原样保留，保证历史数据可直接搬入且 ID 不变。
-- 仅调整数据库归属（单体 teaclub 库 -> wallet-service 独立库）。
-- ============================================================

-- ============================================================
-- V5: 储值钱包域（钱包 / 流水 / 充值方案 / 充值订单）
--
-- 资金安全约束：
--   1. 金额一律 BIGINT 存人民币分
--   2. 钱包双余额：principal_balance（实付）与 gift_balance（赠送）分列
--   3. 每次余额变动必须写 wal_wallet_transaction，含变动前/变动额/变动后
--   4. uk_txn_biz (biz_type, biz_no) 是资金幂等的最终防线
--   5. 流水表无 update_time、无 deleted —— 结构层面禁止修改删除
-- ============================================================

CREATE TABLE wal_wallet (
  id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id             BIGINT UNSIGNED NOT NULL COMMENT '钱包属于品牌',
  member_id            BIGINT UNSIGNED NOT NULL,
  principal_balance    BIGINT NOT NULL DEFAULT 0 COMMENT '实付余额（分）',
  gift_balance         BIGINT NOT NULL DEFAULT 0 COMMENT '赠送余额（分）',
  total_principal_in   BIGINT NOT NULL DEFAULT 0 COMMENT '累计实付充值（分）',
  total_gift_in        BIGINT NOT NULL DEFAULT 0 COMMENT '累计赠送入账（分）',
  total_principal_out  BIGINT NOT NULL DEFAULT 0 COMMENT '累计实付消费（分）',
  total_gift_out       BIGINT NOT NULL DEFAULT 0 COMMENT '累计赠送消费（分）',
  total_refund_in      BIGINT NOT NULL DEFAULT 0 COMMENT '累计退款回冲（分）',
  version              INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  status               VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/FROZEN',
  create_time          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_wallet_member (brand_id, member_id),
  KEY idx_wallet_member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员钱包（品牌级，双余额）';

CREATE TABLE wal_wallet_transaction (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id          BIGINT UNSIGNED NOT NULL,
  wallet_id         BIGINT UNSIGNED NOT NULL,
  member_id         BIGINT UNSIGNED NOT NULL,
  store_id          BIGINT UNSIGNED NULL COMMENT '发生门店',
  txn_no            VARCHAR(64) NOT NULL COMMENT '流水号',
  biz_type          VARCHAR(32) NOT NULL COMMENT 'RECHARGE_IN/CONSUME_OUT/REFUND_IN/RECHARGE_REFUND_OUT/ADMIN_ADJUST',
  biz_no            VARCHAR(64) NOT NULL COMMENT '关联业务单号（幂等键）',
  principal_change  BIGINT NOT NULL DEFAULT 0 COMMENT '实付金变动额（分），正入负出',
  principal_before  BIGINT NOT NULL COMMENT '变动前实付余额',
  principal_after   BIGINT NOT NULL COMMENT '变动后实付余额',
  gift_change       BIGINT NOT NULL DEFAULT 0 COMMENT '赠送金变动额（分）',
  gift_before       BIGINT NOT NULL,
  gift_after        BIGINT NOT NULL,
  amount_paid       BIGINT NOT NULL DEFAULT 0 COMMENT '本次实际支付现金（分）',
  gift_amount       BIGINT NOT NULL DEFAULT 0 COMMENT '本次获赠金额（分）',
  gift_expire_time  DATETIME(3) NULL COMMENT '赠送金过期时间',
  remark            VARCHAR(255) NULL,
  operator_id       BIGINT UNSIGNED NULL,
  operator_type     VARCHAR(16) NOT NULL DEFAULT 'SYSTEM' COMMENT 'MEMBER/STAFF/SYSTEM',
  trace_id          VARCHAR(64) NULL,
  create_time       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_txn_no (txn_no),
  UNIQUE KEY uk_txn_biz (biz_type, biz_no),
  KEY idx_txn_wallet_time (wallet_id, create_time),
  KEY idx_txn_store_time (store_id, create_time),
  KEY idx_txn_member_time (member_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钱包流水（不可修改、不可删除）';

CREATE TABLE wal_recharge_plan (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id            BIGINT UNSIGNED NOT NULL,
  plan_code           VARCHAR(32) NOT NULL,
  plan_name           VARCHAR(64) NOT NULL,
  recharge_amount     BIGINT NOT NULL COMMENT '充值金额（分）',
  gift_amount         BIGINT NOT NULL DEFAULT 0 COMMENT '赠送金额（分）',
  gift_coupon_template_id BIGINT UNSIGNED NULL COMMENT '赠送优惠券模板',
  gift_points         BIGINT NOT NULL DEFAULT 0 COMMENT '赠送积分',
  gift_expire_days    INT NOT NULL DEFAULT 365 COMMENT '赠送金有效期天数，0=永久',
  min_level_id        BIGINT UNSIGNED NULL COMMENT '最低会员等级要求',
  custom_amount_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为自定义金额方案',
  min_custom_amount   BIGINT NOT NULL DEFAULT 0 COMMENT '自定义金额下限（分）',
  max_custom_amount   BIGINT NOT NULL DEFAULT 0 COMMENT '自定义金额上限（分），0=不限',
  per_member_limit    INT NOT NULL DEFAULT 0 COMMENT '每人充值次数上限，0=不限',
  sort                INT NOT NULL DEFAULT 0,
  status              VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  start_time          DATETIME(3) NULL COMMENT '活动开始时间',
  end_time            DATETIME(3) NULL COMMENT '活动结束时间',
  remark              VARCHAR(255) NULL,
  create_time         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted             TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plan_code (brand_id, plan_code),
  KEY idx_plan_status (brand_id, status, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='充值方案';

CREATE TABLE wal_recharge_plan_store (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  plan_id     BIGINT UNSIGNED NOT NULL,
  store_id    BIGINT UNSIGNED NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_plan_store (plan_id, store_id),
  KEY idx_rps_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='充值方案参与门店（无记录表示全部门店）';

CREATE TABLE wal_recharge_order (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id         BIGINT UNSIGNED NOT NULL,
  recharge_no      VARCHAR(64) NOT NULL COMMENT '充值业务单号（幂等键）',
  member_id        BIGINT UNSIGNED NOT NULL,
  store_id         BIGINT UNSIGNED NOT NULL COMMENT '发生门店，必填',
  plan_id          BIGINT UNSIGNED NULL COMMENT '充值方案，自定义金额时为空',
  recharge_amount  BIGINT NOT NULL COMMENT '充值金额（分）',
  gift_amount      BIGINT NOT NULL DEFAULT 0 COMMENT '赠送金额（分）',
  gift_expire_days INT NOT NULL DEFAULT 365,
  pay_channel      VARCHAR(32) NOT NULL COMMENT 'WECHAT_JSAPI/WECHAT_NATIVE/MOCK',
  status           VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PAYING/PAID/CANCELLED/FAILED',
  payment_no       VARCHAR(64) NULL COMMENT '关联支付单号',
  pay_time         DATETIME(3) NULL,
  cancel_time      DATETIME(3) NULL,
  remark           VARCHAR(255) NULL,
  create_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted          TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_recharge_no (recharge_no),
  KEY idx_recharge_member_time (member_id, create_time),
  KEY idx_recharge_store_time (store_id, create_time),
  KEY idx_recharge_status (status, pay_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='充值订单';
