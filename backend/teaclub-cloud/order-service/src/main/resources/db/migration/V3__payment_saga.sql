-- ============================================================
-- V3: 余额支付 Saga 状态机（防止"钱扣了订单未支付"的孤儿扣款）
--
-- 每个订单一条 Saga 记录，唯一键 uk_saga_order 保证不重复。
-- 步骤：DEDUCTING（扣款前） -> DEDUCTED（已扣款） -> ORDER_PAID（订单已更新）
-- 任一环节失败：置 COMPENSATED（已回冲）或 FAILED（待补偿任务处理）。
-- ============================================================

CREATE TABLE IF NOT EXISTS oms_payment_saga (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  saga_id          VARCHAR(64)  NOT NULL COMMENT 'Saga 实例号',
  order_no         VARCHAR(64)  NOT NULL COMMENT '订单号',
  step             VARCHAR(32)  NOT NULL COMMENT 'DEDUCTING/DEDUCTED/ORDER_PAID/COMPENSATED',
  status           VARCHAR(32)  NOT NULL COMMENT 'RUNNING/COMPLETED/COMPENSATED/FAILED',
  principal_amount BIGINT NOT NULL DEFAULT 0 COMMENT '实付扣款额',
  gift_amount      BIGINT NOT NULL DEFAULT 0 COMMENT '赠送扣款额',
  remark           VARCHAR(255) NULL,
  create_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_saga_order (order_no),
  KEY idx_saga_status (status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='余额支付Saga状态机';
