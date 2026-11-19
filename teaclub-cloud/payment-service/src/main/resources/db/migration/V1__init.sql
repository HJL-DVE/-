-- ============================================================
-- payment-service V1: 由单体 backend 的 V6__init_order_payment.sql 拆分而来
--
-- 只保留支付域三张表；订单域归 order-service。
-- 保留原有商户订单号、微信交易号、支付与退款状态字段。
-- ============================================================

-- ============================================================
-- V6: 订单域与支付域
--
-- 关键约束：
--   1. oms_order.store_id 必填 —— 每笔订单必须记录所属门店
--   2. 金额全部为分，且全部由服务端重算后落库
--   3. pay_payment_order (biz_type, biz_no) 唯一 —— 同一业务单只能有一个有效支付单
--   4. pay_notify_log (provider, notify_id) 唯一 —— 重复通知在数据库层被拒绝
--   5. pay_refund_order 记录退款资金构成（实付/赠送/现金）
-- ============================================================

CREATE TABLE pay_payment_order (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id        BIGINT UNSIGNED NOT NULL,
  payment_no      VARCHAR(64) NOT NULL COMMENT '平台支付单号（微信 out_trade_no）',
  biz_type        VARCHAR(32) NOT NULL COMMENT 'ORDER 消费订单 / RECHARGE 充值订单',
  biz_no          VARCHAR(64) NOT NULL COMMENT '业务单号',
  store_id        BIGINT UNSIGNED NULL,
  member_id       BIGINT UNSIGNED NULL,
  channel         VARCHAR(32) NOT NULL COMMENT 'WECHAT_JSAPI/WECHAT_NATIVE/BALANCE/MOCK',
  provider        VARCHAR(32) NOT NULL DEFAULT 'MOCK' COMMENT 'MOCK / WECHAT_V3，区分演示与真实数据',
  amount          BIGINT NOT NULL COMMENT '支付金额（分）',
  currency        VARCHAR(8) NOT NULL DEFAULT 'CNY',
  status          VARCHAR(32) NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/PAYING/SUCCESS/FAILED/CLOSED/REFUNDING/REFUNDED',
  prepay_id       VARCHAR(128) NULL,
  transaction_id  VARCHAR(64) NULL COMMENT '微信支付订单号',
  code_url        VARCHAR(512) NULL COMMENT 'Native 二维码链接',
  payer_openid    VARCHAR(64) NULL,
  success_time    DATETIME(3) NULL,
  fail_reason     VARCHAR(512) NULL,
  expire_time     DATETIME(3) NULL,
  notify_received TINYINT(1) NOT NULL DEFAULT 0,
  create_time     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_payment_no (payment_no),
  UNIQUE KEY uk_payment_biz (biz_type, biz_no),
  UNIQUE KEY uk_payment_txn (transaction_id),
  KEY idx_payment_status (status, create_time),
  KEY idx_payment_member (member_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付单';

CREATE TABLE pay_notify_log (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  provider       VARCHAR(32) NOT NULL DEFAULT 'WECHAT_V3',
  notify_id      VARCHAR(128) NOT NULL COMMENT '通知 ID，幂等键',
  notify_type    VARCHAR(32) NOT NULL COMMENT 'PAY/REFUND',
  event_type     VARCHAR(64) NULL,
  out_trade_no   VARCHAR(64) NULL,
  transaction_id VARCHAR(64) NULL,
  headers_json   JSON NULL COMMENT '仅保留验签相关头，非敏感',
  raw_body       TEXT NULL COMMENT '原始报文',
  decrypt_body   JSON NULL COMMENT '解密后业务 JSON',
  verify_result  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '验签结果',
  process_status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED' COMMENT 'RECEIVED/PROCESSED/IGNORED/FAILED',
  process_msg    VARCHAR(512) NULL,
  retry_count    INT NOT NULL DEFAULT 0,
  create_time    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_notify_id (provider, notify_id),
  KEY idx_notify_trade (out_trade_no),
  KEY idx_notify_status (process_status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付通知记录（幂等与审计）';

CREATE TABLE pay_refund_order (
  id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id                BIGINT UNSIGNED NOT NULL,
  refund_no               VARCHAR(64) NOT NULL COMMENT '退款单号（幂等键）',
  payment_no              VARCHAR(64) NOT NULL COMMENT '原支付单号',
  biz_type                VARCHAR(32) NOT NULL COMMENT 'ORDER/RECHARGE',
  biz_no                  VARCHAR(64) NOT NULL,
  store_id                BIGINT UNSIGNED NULL,
  member_id               BIGINT UNSIGNED NULL,
  channel                 VARCHAR(32) NOT NULL COMMENT 'WECHAT/BALANCE',
  refund_amount           BIGINT NOT NULL COMMENT '本次退款总额（分）',
  refund_principal_amount BIGINT NOT NULL DEFAULT 0 COMMENT '退回实付余额部分（分）',
  refund_gift_amount      BIGINT NOT NULL DEFAULT 0 COMMENT '退回赠送余额部分（分）',
  refund_cash_amount      BIGINT NOT NULL DEFAULT 0 COMMENT '微信原路退回现金部分（分）',
  reason                  VARCHAR(255) NULL,
  status                  VARCHAR(32) NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/PROCESSING/SUCCESS/FAILED/CANCELLED',
  wx_refund_id            VARCHAR(64) NULL COMMENT '微信退款单号',
  refund_status_wx        VARCHAR(32) NULL,
  success_time            DATETIME(3) NULL,
  fail_reason             VARCHAR(512) NULL,
  apply_user_id           BIGINT UNSIGNED NULL,
  audit_user_id           BIGINT UNSIGNED NULL,
  audit_time              DATETIME(3) NULL,
  create_time             DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time             DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_refund_no (refund_no),
  KEY idx_refund_payment (payment_no),
  KEY idx_refund_status (status, create_time),
  KEY idx_refund_biz (biz_type, biz_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款单';
