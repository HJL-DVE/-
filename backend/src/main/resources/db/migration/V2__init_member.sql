-- ============================================================
-- V2: 会员模块
-- ============================================================

CREATE TABLE mem_member_level (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id          BIGINT UNSIGNED NOT NULL,
  level_code        VARCHAR(32)   NOT NULL,
  level_name        VARCHAR(32)   NOT NULL,
  level_no          INT           NOT NULL COMMENT '等级序号，越大越高',
  upgrade_type      VARCHAR(16)   NOT NULL DEFAULT 'CONSUME_AMOUNT',
  upgrade_threshold BIGINT        NOT NULL DEFAULT 0 COMMENT '升级门槛（分）',
  discount_rate     INT           NOT NULL DEFAULT 10000 COMMENT '折扣万分比',
  point_rate        INT           NOT NULL DEFAULT 100 COMMENT '积分倍率百分比',
  icon_url          VARCHAR(512)  NULL,
  benefits          VARCHAR(1024) NULL,
  status            VARCHAR(16)   NOT NULL DEFAULT 'ENABLED',
  create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted           TINYINT(1)    NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_level_code (brand_id, level_code),
  KEY idx_level_no (brand_id, level_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员等级';

CREATE TABLE mem_member (
  id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id             BIGINT UNSIGNED NOT NULL COMMENT '会员属于品牌',
  member_no            VARCHAR(32)   NOT NULL,
  phone                VARCHAR(20)   NOT NULL,
  password             VARCHAR(100)  NULL,
  nickname             VARCHAR(64)   NULL,
  avatar_url           VARCHAR(512)  NULL,
  gender               VARCHAR(16)   NOT NULL DEFAULT 'UNKNOWN',
  birthday             DATE          NULL,
  level_id             BIGINT UNSIGNED NULL,
  register_store_id    BIGINT UNSIGNED NULL COMMENT '注册来源门店',
  register_channel     VARCHAR(16)   NOT NULL DEFAULT 'MINIAPP',
  total_consume_amount BIGINT        NOT NULL DEFAULT 0 COMMENT '累计消费（分）',
  total_recharge_amount BIGINT       NOT NULL DEFAULT 0 COMMENT '累计充值（分）',
  total_order_count    INT           NOT NULL DEFAULT 0,
  points               BIGINT        NOT NULL DEFAULT 0,
  last_consume_time    DATETIME(3)   NULL,
  status               VARCHAR(16)   NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/FROZEN',
  freeze_reason        VARCHAR(255)  NULL,
  remark               VARCHAR(255)  NULL,
  create_time          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted              TINYINT(1)    NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_member_no (brand_id, member_no),
  UNIQUE KEY uk_member_phone (brand_id, phone),
  KEY idx_member_level (level_id),
  KEY idx_member_status_time (brand_id, status, create_time),
  KEY idx_member_last_consume (brand_id, last_consume_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员（品牌级）';

CREATE TABLE mem_member_tag (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id    BIGINT UNSIGNED NOT NULL,
  tag_name    VARCHAR(32)  NOT NULL,
  tag_color   VARCHAR(16)  NULL,
  tag_type    VARCHAR(16)  NOT NULL DEFAULT 'MANUAL',
  remark      VARCHAR(255) NULL,
  create_time DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted     TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tag_name (brand_id, tag_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员标签';

CREATE TABLE mem_member_tag_rel (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  member_id   BIGINT UNSIGNED NOT NULL,
  tag_id      BIGINT UNSIGNED NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_member_tag (member_id, tag_id),
  KEY idx_mtr_tag (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员标签关联';
