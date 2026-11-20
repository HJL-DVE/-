-- ============================================================
-- marketing-service V4: 迁移说明
--
-- 权限数据在 iam_db（auth-service 拥有），本服务的 Flyway 只迁移 marketing_db，
-- 不能跨库插入权限点。
-- 营销权限点由 deploy/sql/marketing-permissions.sql 在 iam_db 上执行（幂等）。
--
-- 本文件仅留下显式记录，避免后来者误以为权限会自动生成。
-- ============================================================

CREATE TABLE IF NOT EXISTS mkt_schema_note (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  note        VARCHAR(255) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='迁移说明';

INSERT INTO mkt_schema_note (note)
SELECT 'V4: 营销权限点需在 iam_db 执行 deploy/sql/marketing-permissions.sql'
WHERE NOT EXISTS (SELECT 1 FROM mkt_schema_note WHERE note LIKE 'V4:%');
