-- ============================================================
-- V3: 钱包冻结记录表
--
-- 设计原则（规格明确要求）：
--   不能直接通过修改余额来模拟冻结 —— 那会破坏"余额 = 流水累计"不变量。
-- 本表独立记录冻结金额，可用余额 = 账户余额 - SUM(有效冻结金额)。
-- 冻结本身不写资金流水（钱没动），只有确认扣款/退款回冲才写流水。
-- ============================================================

CREATE TABLE IF NOT EXISTS wal_wallet_freeze (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id          BIGINT UNSIGNED NOT NULL,
  wallet_id         BIGINT UNSIGNED NOT NULL,
  member_id         BIGINT UNSIGNED NOT NULL,
  order_no          VARCHAR(64)  NOT NULL COMMENT '业务单号（冻结幂等键）',
  freeze_principal  BIGINT NOT NULL DEFAULT 0 COMMENT '冻结实付余额（分）',
  freeze_gift       BIGINT NOT NULL DEFAULT 0 COMMENT '冻结赠送余额（分）',
  status            VARCHAR(16)  NOT NULL DEFAULT 'FROZEN' COMMENT 'FROZEN/CONFIRMED/RELEASED',
  confirmed_txn_no  VARCHAR(64)  NULL COMMENT '确认扣款时生成的资金流水号',
  expire_time       DATETIME(3)  NULL COMMENT '冻结过期时间（超时自动释放）',
  remark            VARCHAR(255) NULL,
  create_time       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_freeze_order (order_no),
  KEY idx_freeze_status_expire (status, expire_time),
  KEY idx_freeze_wallet (wallet_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钱包冻结记录（不动账户余额）';
