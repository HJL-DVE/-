USE iam_db;

-- ============================================================
-- V5: 品牌退款规则配置
--
-- 这些规则由「老板」在后台配置，决定储值卡退款的行为：
--   refund_need_audit      退卡是否需要人工审核（1 需审核 / 0 自助秒退）
--   refund_daily_limit     同一会员每日可办理退卡次数（0 表示不限）
--   refund_gift_consumed_action  赠送金已被消费时的处置：REJECT 拒绝转人工 / ALLOW 允许扣回剩余
--
-- 默认值与 docs/04 的规则一致：需审核、每日 1 次、拒绝转人工。
-- ============================================================

-- 幂等写法：列已存在时跳过，避免重复执行失败
SET @dc = (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = 'iam_db' AND table_name = 'sys_brand' AND column_name = 'refund_need_audit');
SET @sql = IF(@dc = 0,
  'ALTER TABLE sys_brand ADD COLUMN refund_need_audit TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''储值卡退款是否需要人工审核：1 需要 0 自助秒退'' AFTER wallet_deduct_order',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @dc2 = (SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema = 'iam_db' AND table_name = 'sys_brand' AND column_name = 'refund_daily_limit');
SET @sql2 = IF(@dc2 = 0,
  'ALTER TABLE sys_brand ADD COLUMN refund_daily_limit INT NOT NULL DEFAULT 1 COMMENT ''同一会员每日可办理退卡次数，0 表示不限'' AFTER refund_need_audit',
  'SELECT 1');
PREPARE stmt FROM @sql2; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @dc3 = (SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema = 'iam_db' AND table_name = 'sys_brand' AND column_name = 'refund_gift_consumed_action');
SET @sql3 = IF(@dc3 = 0,
  'ALTER TABLE sys_brand ADD COLUMN refund_gift_consumed_action VARCHAR(16) NOT NULL DEFAULT ''REJECT'' COMMENT ''赠送金已被消费时：REJECT 拒绝转人工 / ALLOW 按剩余扣回'' AFTER refund_daily_limit',
  'SELECT 1');
PREPARE stmt FROM @sql3; EXECUTE stmt; DEALLOCATE PREPARE stmt;