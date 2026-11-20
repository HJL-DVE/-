-- 阶段八：经营报表库与跨库只读账号（幂等，可重跑）
CREATE DATABASE IF NOT EXISTS report_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'tc_report'@'%' IDENTIFIED BY 'TcReport_2026';
GRANT ALL PRIVILEGES ON report_db.* TO 'tc_report'@'%';
GRANT SELECT ON order_db.* TO 'tc_report'@'%';
GRANT SELECT ON wallet_db.* TO 'tc_report'@'%';
GRANT SELECT ON member_db.* TO 'tc_report'@'%';
GRANT SELECT ON marketing_db.* TO 'tc_report'@'%';
GRANT SELECT ON payment_db.* TO 'tc_report'@'%';
GRANT SELECT ON iam_db.* TO 'tc_report'@'%';
GRANT SELECT ON product_db.* TO 'tc_report'@'%';
FLUSH PRIVILEGES;
