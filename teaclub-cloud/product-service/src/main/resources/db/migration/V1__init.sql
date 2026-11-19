-- ============================================================
-- product-service V1: 由单体 backend 的 V3__init_product.sql 迁移而来
--
-- 说明：表结构原样保留，保证历史数据可直接搬入且 ID 不变。
-- 仅调整数据库归属（单体 teaclub 库 -> product-service 独立库）。
-- ============================================================

-- ============================================================
-- V3: 商品模块（分类 / SPU / SKU / 门店商品 / 库存）
-- ============================================================

CREATE TABLE prd_category (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id      BIGINT UNSIGNED NOT NULL,
  parent_id     BIGINT UNSIGNED NOT NULL DEFAULT 0,
  category_name VARCHAR(64)  NOT NULL,
  icon_url      VARCHAR(512) NULL,
  sort          INT          NOT NULL DEFAULT 0,
  status        VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
  create_time   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted       TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_cat_brand_parent (brand_id, parent_id, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类';

CREATE TABLE prd_spu (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id    BIGINT UNSIGNED NOT NULL,
  category_id BIGINT UNSIGNED NOT NULL,
  spu_code    VARCHAR(32)   NOT NULL,
  spu_name    VARCHAR(128)  NOT NULL,
  subtitle    VARCHAR(255)  NULL,
  description TEXT          NULL,
  main_image  VARCHAR(512)  NULL,
  images_json JSON          NULL,
  base_price  BIGINT        NOT NULL COMMENT '总部建议起售价（分）',
  min_price   BIGINT        NOT NULL DEFAULT 0 COMMENT '最低保护价（分）',
  unit        VARCHAR(16)   NOT NULL DEFAULT '杯',
  has_sku     TINYINT(1)    NOT NULL DEFAULT 1,
  sort        INT           NOT NULL DEFAULT 0,
  status      VARCHAR(16)   NOT NULL DEFAULT 'ON_SALE',
  create_time DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted     TINYINT(1)    NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_spu_code (brand_id, spu_code),
  KEY idx_spu_cat (category_id, status, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品 SPU';

CREATE TABLE prd_sku (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id    BIGINT UNSIGNED NOT NULL,
  spu_id      BIGINT UNSIGNED NOT NULL,
  sku_code    VARCHAR(32)   NOT NULL,
  sku_name    VARCHAR(128)  NOT NULL,
  spec_json   JSON          NULL,
  price       BIGINT        NOT NULL COMMENT '总部建议价（分）',
  cost_price  BIGINT        NOT NULL DEFAULT 0,
  image_url   VARCHAR(512)  NULL,
  sort        INT           NOT NULL DEFAULT 0,
  status      VARCHAR(16)   NOT NULL DEFAULT 'ENABLED',
  create_time DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted     TINYINT(1)    NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sku_code (brand_id, sku_code),
  KEY idx_sku_spu (spu_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品 SKU';

CREATE TABLE prd_store_spu (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id    BIGINT UNSIGNED NOT NULL,
  store_id    BIGINT UNSIGNED NOT NULL,
  spu_id      BIGINT UNSIGNED NOT NULL,
  is_on_sale  TINYINT(1) NOT NULL DEFAULT 1,
  sort        INT        NOT NULL DEFAULT 0,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted     TINYINT(1)  NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_store_spu (store_id, spu_id),
  KEY idx_ss_spu (spu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门店商品上下架';

CREATE TABLE prd_store_sku (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id    BIGINT UNSIGNED NOT NULL,
  store_id    BIGINT UNSIGNED NOT NULL,
  sku_id      BIGINT UNSIGNED NOT NULL,
  store_price BIGINT      NOT NULL COMMENT '门店售价（分）',
  is_on_sale  TINYINT(1)  NOT NULL DEFAULT 1,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted     TINYINT(1)  NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_store_sku (store_id, sku_id),
  KEY idx_sts_sku (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门店 SKU 差异化定价';

CREATE TABLE prd_inventory (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id         BIGINT UNSIGNED NOT NULL,
  store_id         BIGINT UNSIGNED NOT NULL,
  sku_id           BIGINT UNSIGNED NOT NULL,
  quantity         INT NOT NULL DEFAULT 0,
  locked_quantity  INT NOT NULL DEFAULT 0,
  warning_quantity INT NOT NULL DEFAULT 0,
  version          INT NOT NULL DEFAULT 0,
  create_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_inventory (store_id, sku_id),
  KEY idx_inv_sku (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门店 SKU 库存';
