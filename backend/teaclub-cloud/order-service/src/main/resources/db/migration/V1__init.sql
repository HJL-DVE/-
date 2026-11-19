-- ============================================================
-- order-service V1: 由单体 backend 的 V6__init_order_payment.sql 拆分而来
--
-- 只保留订单域三张表；支付域三张表归 payment-service。
-- 表结构原样保留，保证历史订单（含商品快照与金额）可直接搬入。
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

CREATE TABLE oms_order (
  id                        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id                  BIGINT UNSIGNED NOT NULL,
  order_no                  VARCHAR(64) NOT NULL COMMENT '订单号（业务唯一）',
  store_id                  BIGINT UNSIGNED NOT NULL COMMENT '所属门店，必填',
  member_id                 BIGINT UNSIGNED NULL COMMENT '会员，散客可为空',
  order_source              VARCHAR(16) NOT NULL COMMENT 'MINIAPP/POS/ADMIN',
  total_amount              BIGINT NOT NULL DEFAULT 0 COMMENT '商品总额（分）',
  discount_amount           BIGINT NOT NULL DEFAULT 0 COMMENT '优惠合计（分）',
  coupon_amount             BIGINT NOT NULL DEFAULT 0 COMMENT '券优惠（分）',
  level_discount_amount     BIGINT NOT NULL DEFAULT 0 COMMENT '等级折扣优惠（分）',
  activity_discount_amount  BIGINT NOT NULL DEFAULT 0 COMMENT '活动优惠（分）',
  payable_amount            BIGINT NOT NULL DEFAULT 0 COMMENT '应付金额（分）',
  paid_amount               BIGINT NOT NULL DEFAULT 0 COMMENT '实付金额（分）',
  refunded_amount           BIGINT NOT NULL DEFAULT 0 COMMENT '已退款金额（分）',
  balance_principal_amount  BIGINT NOT NULL DEFAULT 0 COMMENT '余额支付中的实付金部分（分）',
  balance_gift_amount       BIGINT NOT NULL DEFAULT 0 COMMENT '余额支付中的赠送金部分（分）',
  status                    VARCHAR(32) NOT NULL DEFAULT 'UNPAID' COMMENT 'UNPAID/PAID/COMPLETED/CANCELLED/REFUNDING/REFUNDED',
  pay_channel               VARCHAR(32) NULL COMMENT 'BALANCE/WECHAT_JSAPI/WECHAT_NATIVE/MOCK',
  payment_no                VARCHAR(64) NULL,
  member_coupon_id          BIGINT UNSIGNED NULL COMMENT '使用的会员券',
  points_earned             BIGINT NOT NULL DEFAULT 0 COMMENT '本单获得积分',
  pickup_no                 VARCHAR(16) NULL COMMENT '取餐号',
  pay_time                  DATETIME(3) NULL,
  complete_time             DATETIME(3) NULL,
  cancel_time               DATETIME(3) NULL,
  cancel_reason             VARCHAR(255) NULL,
  remark                    VARCHAR(255) NULL,
  operator_id               BIGINT UNSIGNED NULL COMMENT '收银员（POS 下单）',
  create_time               DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time               DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted                   TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_order_store_time (store_id, create_time),
  KEY idx_order_member_time (member_id, create_time),
  KEY idx_order_status_pay (status, pay_time),
  KEY idx_order_brand_time (brand_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单（必含 store_id）';

CREATE TABLE oms_order_item (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id          BIGINT UNSIGNED NOT NULL,
  order_id          BIGINT UNSIGNED NOT NULL,
  order_no          VARCHAR(64) NOT NULL,
  store_id          BIGINT UNSIGNED NOT NULL,
  spu_id            BIGINT UNSIGNED NOT NULL,
  sku_id            BIGINT UNSIGNED NOT NULL,
  spu_name          VARCHAR(128) NOT NULL COMMENT '商品名称快照',
  sku_name          VARCHAR(128) NOT NULL COMMENT '规格名称快照',
  spec_json         JSON NULL,
  image_url         VARCHAR(512) NULL,
  unit_price        BIGINT NOT NULL COMMENT '成交单价（分）',
  quantity          INT NOT NULL,
  total_amount      BIGINT NOT NULL COMMENT '行小计（分）',
  discount_amount   BIGINT NOT NULL DEFAULT 0 COMMENT '行级优惠分摊（分）',
  actual_amount     BIGINT NOT NULL COMMENT '行实付（分）',
  refunded_quantity INT NOT NULL DEFAULT 0 COMMENT '已退数量',
  create_time       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_item_order (order_id),
  KEY idx_item_sku_time (sku_id, create_time),
  KEY idx_item_store_time (store_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细';

CREATE TABLE oms_order_status_log (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id      BIGINT UNSIGNED NOT NULL,
  order_id      BIGINT UNSIGNED NOT NULL,
  order_no      VARCHAR(64) NOT NULL,
  from_status   VARCHAR(32) NULL,
  to_status     VARCHAR(32) NOT NULL,
  operator_id   BIGINT UNSIGNED NULL,
  operator_type VARCHAR(16) NOT NULL DEFAULT 'SYSTEM',
  remark        VARCHAR(255) NULL,
  create_time   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_status_log_order (order_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单状态流转日志';

