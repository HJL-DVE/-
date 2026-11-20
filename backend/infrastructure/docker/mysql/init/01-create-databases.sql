-- ============================================================
-- TeaClub 微服务数据库初始化
-- 容器首次启动时由 /docker-entrypoint-initdb.d 自动执行
--
-- 设计要点：
--   1. 每个微服务一个独立库（逻辑隔离）
--   2. 每个服务一个独立账号，只授权自己的库（权限隔离）
--   3. 账号无法跨库查询，物理上杜绝跨服务直连
-- ============================================================

SET NAMES utf8mb4;

-- ============ 1. 建库 ============
CREATE DATABASE IF NOT EXISTS iam_db        DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS member_db     DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS product_db    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS wallet_db     DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS order_db      DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS payment_db    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS marketing_db  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS inventory_db  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS report_db     DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ============ 2. 建账号（密码仅用于本地开发，生产必须更换）============
-- 注意：'%' 允许容器网络内互访；生产应限定 IP
CREATE USER IF NOT EXISTS 'tc_auth'@'%'       IDENTIFIED BY 'ok4GC3nLentKqERnFHuhRA';
CREATE USER IF NOT EXISTS 'tc_member'@'%'     IDENTIFIED BY 'puerITGf4erlA5HOiigmQ';
CREATE USER IF NOT EXISTS 'tc_product'@'%'    IDENTIFIED BY 'a7U5R9dD0k7vQn760AFMZg';
CREATE USER IF NOT EXISTS 'tc_wallet'@'%'     IDENTIFIED BY 'dMOhp9kkZ3XugyqGgHzVCA';
CREATE USER IF NOT EXISTS 'tc_order'@'%'      IDENTIFIED BY 'H1XRvlFm082zIKs8xBi0Q';
CREATE USER IF NOT EXISTS 'tc_payment'@'%'    IDENTIFIED BY 'c8vnMQFaWzxKewaGcZljg';
CREATE USER IF NOT EXISTS 'tc_marketing'@'%'  IDENTIFIED BY 'TcMarketing_2026';
CREATE USER IF NOT EXISTS 'tc_inventory'@'%'  IDENTIFIED BY 'TcInventory_2026';
CREATE USER IF NOT EXISTS 'tc_report'@'%'     IDENTIFIED BY 'TcReport_2026';

-- ============ 3. 授予各自库权限（严禁跨库）============
GRANT ALL PRIVILEGES ON iam_db.*       TO 'tc_auth'@'%';
GRANT ALL PRIVILEGES ON member_db.*    TO 'tc_member'@'%';
GRANT ALL PRIVILEGES ON product_db.*   TO 'tc_product'@'%';
GRANT ALL PRIVILEGES ON wallet_db.*    TO 'tc_wallet'@'%';
GRANT ALL PRIVILEGES ON order_db.*     TO 'tc_order'@'%';
GRANT ALL PRIVILEGES ON payment_db.*   TO 'tc_payment'@'%';
GRANT ALL PRIVILEGES ON marketing_db.* TO 'tc_marketing'@'%';
GRANT ALL PRIVILEGES ON inventory_db.* TO 'tc_inventory'@'%';
GRANT ALL PRIVILEGES ON report_db.*    TO 'tc_report'@'%';

-- ============ 4. 迁移用账号（需跨库读写，仅迁移期临时使用）============
-- 注意：MySQL 不支持一条 GRANT 里写多个 db.*（旧写法在此处静默失败，
-- 账号建出来却只有 USAGE）。必须按库逐条授权。
-- 口径：跨库【只读】，供 DataGrip / 排查使用；迁移期如需读写请另建独立账号。
CREATE USER IF NOT EXISTS 'tc_migrator'@'%' IDENTIFIED BY 'TcMigrator_2026';
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

-- ============ 5. 自检 ============
SELECT '=== databases created ===' AS info;
SELECT schema_name FROM information_schema.schemata
WHERE schema_name LIKE '%\_db' ORDER BY schema_name;
