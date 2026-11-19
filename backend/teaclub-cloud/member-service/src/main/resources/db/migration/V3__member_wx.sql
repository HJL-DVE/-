-- ======
-- V3: 微信小程序会员绑定（openid <-> 会员）
--
-- 只新增表，不改动既有 mem_member / mem_member_level 结构。
-- 同一品牌下 openid 唯一；unionid 为开放平台跨应用识别预留（当前版本可为空）。
-- ======

CREATE TABLE mem_member_wx (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id    BIGINT UNSIGNED NOT NULL,
  openid      VARCHAR(64)   NOT NULL COMMENT '微信小程序 openid',
  member_id   BIGINT UNSIGNED NOT NULL COMMENT '关联会员 ID（mem_member.id）',
  unionid     VARCHAR(64)   NULL COMMENT '微信 unionid（开放平台绑定后写入）',
  create_time DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_wx_openid_brand (brand_id, openid),
  KEY idx_wx_member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员微信绑定';
