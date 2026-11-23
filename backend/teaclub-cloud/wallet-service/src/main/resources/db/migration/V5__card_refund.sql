-- ============================================================
-- V5: 会员退卡（储值卡注销退余额）
--
-- 业务规则（与 docs/04 一致）：
--   实付本金（principal）   → 可退，退回原支付渠道
--   赠送金额（gift）        → 营销成本，作废不折现
--
-- 流程：会员小程序提交申请 → 店长/总部审核 → 通过后调用
--       walletService.rechargeRefundOut 扣回余额并生成资金流水
--
-- 为什么要独立申请表而不是直接扣款：
--   退卡是大额资金流出，必须有申请、审核、留痕三段式，
--   且审核前余额仍可正常消费（不冻结），审核时再校验可否足额扣回。
-- ============================================================

CREATE TABLE wal_card_refund (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id          BIGINT UNSIGNED NOT NULL,
  member_id         BIGINT UNSIGNED NOT NULL,
  refund_no         VARCHAR(64) NOT NULL COMMENT '退卡申请单号',
  principal_amount  BIGINT NOT NULL DEFAULT 0 COMMENT '申请退还的实付本金（分）',
  gift_void_amount  BIGINT NOT NULL DEFAULT 0 COMMENT '作废的赠送金额（分）',
  status            VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  apply_reason      VARCHAR(255) NULL COMMENT '申请原因',
  audit_by          BIGINT UNSIGNED NULL COMMENT '审核人',
  audit_time        DATETIME(3) NULL,
  audit_remark      VARCHAR(255) NULL,
  refund_txn_no     VARCHAR(64) NULL COMMENT '审核通过后生成的资金流水号',
  version           INT NOT NULL DEFAULT 0,
  create_time       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_card_refund_no (refund_no),
  KEY idx_card_refund_member (member_id, create_time),
  KEY idx_card_refund_status (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员退卡申请';

-- 流水业务类型扩展：退卡退款复用 RECHARGE_REFUND_OUT，无需改枚举
