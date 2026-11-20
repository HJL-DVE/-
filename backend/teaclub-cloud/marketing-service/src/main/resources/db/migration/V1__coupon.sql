-- ============================================================
-- marketing-service V1: 优惠券域
--
-- 设计要点：
--   1. 金额一律 BIGINT「分」，禁止浮点
--   2. 模板（template）与实例（coupon）分离：模板是规则，实例是会员手里那张券
--   3. 券状态机：UNUSED -> LOCKED -> USED / 释放回 UNUSED / EXPIRED
--   4. 幂等三道防线：
--      a) Redis  key（快速拦截）
--      b) 唯一索引（最终底线）
--      c) 条件 UPDATE（status 必须匹配才生效）
--   5. 门店/商品/等级限制用关联表，不用 JSON 字符串（可索引、可 JOIN）
-- ============================================================

-- ---------- 优惠券模板 ----------
CREATE TABLE mkt_coupon_template (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id            BIGINT UNSIGNED NOT NULL,
  template_no         VARCHAR(64)  NOT NULL COMMENT '模板编号（业务主键）',
  coupon_name         VARCHAR(128) NOT NULL,
  coupon_type         VARCHAR(32)  NOT NULL COMMENT 'FULL_REDUCTION/DISCOUNT/NO_THRESHOLD/GOODS_EXCHANGE',
  scope_type          VARCHAR(16)  NOT NULL DEFAULT 'ALL' COMMENT 'ALL/STORE/GOODS 适用范围类型',
  face_value          BIGINT       NOT NULL DEFAULT 0 COMMENT '面额（分）：满减/无门槛/兑换券使用',
  discount_rate       INT          NOT NULL DEFAULT 10000 COMMENT '折扣率万分数：9500=95 折，仅折扣券',
  max_discount_amount BIGINT       NULL COMMENT '折扣券封顶金额（分），NULL=不封顶',
  threshold_amount    BIGINT       NOT NULL DEFAULT 0 COMMENT '满减门槛（分），0=无门槛',
  exchange_spu_id     BIGINT       NULL COMMENT '兑换券可兑换的 SPU',
  exchange_sku_id     BIGINT       NULL COMMENT '兑换券可兑换的 SKU（优先于 SPU）',
  total_count         INT          NOT NULL DEFAULT 0 COMMENT '发行总量，0=不限',
  issued_count        INT          NOT NULL DEFAULT 0 COMMENT '已发放数量（发行时原子 +1）',
  per_member_limit    INT          NOT NULL DEFAULT 1 COMMENT '每人限领张数',
  valid_type          VARCHAR(16)  NOT NULL DEFAULT 'RELATIVE' COMMENT 'FIXED=固定区间 / RELATIVE=领取后 N 天',
  valid_start         DATETIME(3)  NULL COMMENT 'FIXED 时的生效时间',
  valid_end           DATETIME(3)  NULL COMMENT 'FIXED 时的失效时间',
  valid_days          INT          NULL COMMENT 'RELATIVE 时的有效天数',
  new_member_only     TINYINT      NOT NULL DEFAULT 0 COMMENT '1=仅新人可领',
  birthday_only       TINYINT      NOT NULL DEFAULT 0 COMMENT '1=生日券',
  status              VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ENABLED/DISABLED',
  remark              VARCHAR(255) NULL,
  create_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted             TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_template_no (template_no),
  KEY idx_template_brand_status (brand_id, status),
  KEY idx_template_type (coupon_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券模板';

-- ---------- 模板适用门店（scope_type=STORE 时生效）----------
CREATE TABLE mkt_coupon_template_store (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id    BIGINT UNSIGNED NOT NULL,
  template_id BIGINT UNSIGNED NOT NULL,
  store_id    BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_template_store (template_id, store_id),
  KEY idx_cts_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模板适用门店';

-- ---------- 模板适用商品（scope_type=GOODS 时生效）----------
CREATE TABLE mkt_coupon_template_spu (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id    BIGINT UNSIGNED NOT NULL,
  template_id BIGINT UNSIGNED NOT NULL,
  spu_id      BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_template_spu (template_id, spu_id),
  KEY idx_cts_spu (spu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模板适用商品';

-- ---------- 模板会员等级限制（为空=不限等级）----------
CREATE TABLE mkt_coupon_template_level (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id    BIGINT UNSIGNED NOT NULL,
  template_id BIGINT UNSIGNED NOT NULL,
  level_id    BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_template_level (template_id, level_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模板会员等级限制';

-- ---------- 会员持有的券实例 ----------
CREATE TABLE mkt_coupon (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id           BIGINT UNSIGNED NOT NULL,
  coupon_no          VARCHAR(64)  NOT NULL COMMENT '券码（唯一）',
  template_id        BIGINT UNSIGNED NOT NULL,
  coupon_type        VARCHAR(32)  NOT NULL COMMENT '冗余模板类型，避免每次 JOIN',
  member_id          BIGINT UNSIGNED NOT NULL,
  face_value         BIGINT       NOT NULL DEFAULT 0 COMMENT '冗余面额（分）',
  discount_rate      INT          NOT NULL DEFAULT 10000,
  max_discount_amount BIGINT      NULL,
  threshold_amount   BIGINT       NOT NULL DEFAULT 0,
  scope_type         VARCHAR(16)  NOT NULL DEFAULT 'ALL',
  exchange_spu_id    BIGINT       NULL,
  exchange_sku_id    BIGINT       NULL,
  status             VARCHAR(16)  NOT NULL DEFAULT 'UNUSED' COMMENT 'UNUSED/LOCKED/USED/EXPIRED',
  obtain_type        VARCHAR(16)  NOT NULL DEFAULT 'RECEIVE' COMMENT 'RECEIVE=主动领取/GRANT=后台发放/ACTIVITY=活动赠送',
  obtain_time        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  valid_start        DATETIME(3)  NOT NULL,
  valid_end          DATETIME(3)  NOT NULL,
  locked_order_no    VARCHAR(64)  NULL COMMENT '锁定该券的订单号',
  locked_time        DATETIME(3)  NULL,
  used_order_no      VARCHAR(64)  NULL COMMENT '核销订单号',
  used_time          DATETIME(3)  NULL,
  discount_amount    BIGINT       NULL COMMENT '实际抵扣金额（分）',
  version            INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  create_time        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_coupon_no (coupon_no),
  -- 「一个订单只能锁/核销一张券」由业务保证；同一张券对同一订单重复锁定由以下索引兜底
  KEY idx_coupon_member_status (member_id, status),
  KEY idx_coupon_template_member (template_id, member_id),
  KEY idx_coupon_locked_order (locked_order_no),
  KEY idx_coupon_used_order (used_order_no),
  KEY idx_coupon_valid_end (valid_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员优惠券实例';

-- ---------- 领取记录（幂等 + 计数依据）----------
CREATE TABLE mkt_coupon_receive_record (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id    BIGINT UNSIGNED NOT NULL,
  template_id BIGINT UNSIGNED NOT NULL,
  member_id   BIGINT UNSIGNED NOT NULL,
  coupon_id   BIGINT UNSIGNED NOT NULL,
  coupon_no   VARCHAR(64)  NOT NULL,
  qty         INT          NOT NULL DEFAULT 1 COMMENT '本次领取张数',
  create_time DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_crr_template_member (template_id, member_id),
  KEY idx_crr_coupon (coupon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券领取记录';

-- ---------- 券操作流水（锁定/核销/释放/过期，便于对账与排障）----------
CREATE TABLE mkt_coupon_log (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id    BIGINT UNSIGNED NOT NULL,
  coupon_id   BIGINT UNSIGNED NOT NULL,
  coupon_no   VARCHAR(64)  NOT NULL,
  member_id   BIGINT UNSIGNED NOT NULL,
  action      VARCHAR(16)  NOT NULL COMMENT 'LOCK/UNLOCK/USE/RELEASE/EXPIRE',
  order_no    VARCHAR(64)  NULL,
  amount      BIGINT       NULL COMMENT '本次涉及的金额（分）',
  from_status VARCHAR(16)  NULL,
  to_status   VARCHAR(16)  NULL,
  trace_id    VARCHAR(64)  NULL,
  remark      VARCHAR(255) NULL,
  create_time DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_clog_coupon (coupon_id),
  KEY idx_clog_order (order_no),
  KEY idx_clog_member (member_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券操作流水';
