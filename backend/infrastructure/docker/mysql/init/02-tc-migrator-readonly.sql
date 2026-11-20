-- ============================================================
-- tc_migrator：跨库只读账号（DataGrip / 排查用）
--
-- 背景：init 脚本最初把这条写成了多库单条 GRANT：
--   GRANT SELECT, ... ON iam_db.*, member_db.*, ... TO 'tc_migrator'@'%';
-- MySQL 不支持一条 GRANT 里写多个 db.*，该语句报错，
-- 账号建出来了却只有 USAGE 权限。此脚本按库逐条授权修复。
--
-- 权限口径：仅 SELECT + SHOW VIEW（只读），不含任何写权限。
-- 本脚本幂等，可重复执行。
-- ============================================================

SET NAMES utf8mb4;

GRANT SELECT, SHOW VIEW ON iam_db.*       TO 'tc_migrator'@'%';
GRANT SELECT, SHOW VIEW ON member_db.*    TO 'tc_migrator'@'%';
GRANT SELECT, SHOW VIEW ON product_db.*   TO 'tc_migrator'@'%';
GRANT SELECT, SHOW VIEW ON wallet_db.*    TO 'tc_migrator'@'%';
GRANT SELECT, SHOW VIEW ON order_db.*     TO 'tc_migrator'@'%';
GRANT SELECT, SHOW VIEW ON payment_db.*   TO 'tc_migrator'@'%';
GRANT SELECT, SHOW VIEW ON marketing_db.* TO 'tc_migrator'@'%';
GRANT SELECT, SHOW VIEW ON inventory_db.* TO 'tc_migrator'@'%';
GRANT SELECT, SHOW VIEW ON report_db.*    TO 'tc_migrator'@'%';

FLUSH PRIVILEGES;
