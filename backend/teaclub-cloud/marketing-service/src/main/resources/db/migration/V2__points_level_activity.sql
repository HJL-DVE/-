-- ============================================================
-- marketing-service V2: 积分 / 等级权益 / 营销活动
--
-- 积分设计：
--   a) mkt_points_account 是【唯一事实来源】，余额 = 流水累计（可校验）
--   b) 每笔流水记 before/after，保证链式衔接可验证
--   c) uk_points_biz (biz_type, biz_no) 是「同一订单不重复发积分」的最终底线
--   d) 退款冲正：按原发放流水冲正，不重新计算，保证金额严格等于原值
--   e) 过期按批次（mkt_points_batch），先到期先消耗
-- ============================================================

-- ---------- 积分账户 ----------
CREATE TABLE mkt_points_account (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id       BIGINT UNSIGNED NOT NULL,
  member_id      BIGINT UNSIGNED NOT NULL,
  total_points   BIGINT NOT NULL DEFAULT 0 COMMENT '可用积分余额',
  total_earned   BIGINT NOT NULL DEFAULT 0 COMMENT '累计获得',
  total_consumed BIGINT NOT NULL DEFAULT 0 COMMENT '累计消耗（兑换/扣减/过期）',
  total_reversed BIGINT NOT NULL DEFAULT 0 COMMENT '累计冲正（退款扣回）',
  version        INT    NOT NULL DEFAULT 0 COMMENT '乐观锁',
  create_time    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_points_member (brand_id, member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员积分账户';

-- ---------- 积分流水 ----------
CREATE TABLE mkt_points_transaction (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id       BIGINT UNSIGNED NOT NULL,
  account_id     BIGINT UNSIGNED NOT NULL,
  member_id      BIGINT UNSIGNED NOT NULL,
  txn_no         VARCHAR(64) NOT NULL COMMENT '流水号',
  biz_type       VARCHAR(32) NOT NULL COMMENT 'ORDER_EARN/ORDER_REFUND_REVERSE/SIGN_IN/ACTIVITY/EXCHANGE/DEDUCT/EXPIRE/ADJUST',
  biz_no         VARCHAR(64) NULL COMMENT '业务单号（订单号/签到日期/兑换单号）',
  change_points  BIGINT NOT NULL COMMENT '变动值，正=增加，负=减少',
  before_points  BIGINT NOT NULL COMMENT '变动前',
  after_points   BIGINT NOT NULL COMMENT '变动后',
  store_id       BIGINT UNSIGNED NULL,
  remark         VARCHAR(255) NULL,
  trace_id       VARCHAR(64) NULL,
  create_time    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_points_txn_no (txn_no),
  -- 同一业务单同一类型只能记一次：重复发积分的最终防线
  UNIQUE KEY uk_points_biz (brand_id, biz_type, biz_no),
  KEY idx_pt_member (member_id, create_time),
  KEY idx_pt_biz_no (biz_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分流水';

-- ---------- 积分批次（过期处理）----------
CREATE TABLE mkt_points_batch (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id       BIGINT UNSIGNED NOT NULL,
  member_id      BIGINT UNSIGNED NOT NULL,
  source_txn_id  BIGINT UNSIGNED NOT NULL COMMENT '来源流水（获得积分那笔）',
  points         BIGINT NOT NULL COMMENT '本批原始积分',
  remain_points  BIGINT NOT NULL COMMENT '剩余可用积分',
  expire_time    DATETIME(3) NOT NULL,
  status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/EXHAUSTED/EXPIRED',
  create_time    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_pb_member_expire (member_id, expire_time, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分批次（先进先出过期）';

-- ---------- 积分规则（按品牌配置）----------
CREATE TABLE mkt_points_rule (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id          BIGINT UNSIGNED NOT NULL,
  rule_code         VARCHAR(64) NOT NULL COMMENT 'ORDER_EARN/SIGN_IN/ACTIVITY',
  rule_name         VARCHAR(128) NOT NULL,
  -- 消费积分：每消费 N 分钱得 M 积分
  amount_per_point  BIGINT NOT NULL DEFAULT 100 COMMENT '每消费多少分（1 元=100 分）得 1 积分',
  sign_in_points    BIGINT NOT NULL DEFAULT 0 COMMENT '签到基础积分',
  sign_in_continuous_max INT NOT NULL DEFAULT 7 COMMENT '连续签到加成上限天数',
  sign_in_bonus     BIGINT NOT NULL DEFAULT 0 COMMENT '连续签到每天额外加成积分',
  expire_days       INT NOT NULL DEFAULT 365 COMMENT '积分有效期天数，0=永不过期',
  status            VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
  create_time       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted           TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_points_rule (brand_id, rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分规则';

-- ---------- 签到记录 ----------
CREATE TABLE mkt_sign_in (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id      BIGINT UNSIGNED NOT NULL,
  member_id     BIGINT UNSIGNED NOT NULL,
  sign_date     DATE NOT NULL,
  continuous_days INT NOT NULL DEFAULT 1 COMMENT '截至当日的连续签到天数',
  points        BIGINT NOT NULL DEFAULT 0 COMMENT '本次获得积分',
  create_time   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  -- 同一天只能签一次：幂等底线
  UNIQUE KEY uk_sign_member_date (brand_id, member_id, sign_date),
  KEY idx_sign_member (member_id, sign_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员签到记录';

-- ---------- 等级权益（等级定义仍在 member-service，这里只放营销侧权益）----------
CREATE TABLE mkt_level_benefit (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id      BIGINT UNSIGNED NOT NULL,
  level_id      BIGINT UNSIGNED NOT NULL COMMENT 'member-service 的 mem_member_level.id',
  benefit_code  VARCHAR(64) NOT NULL COMMENT 'BIRTHDAY_COUPON/POINT_RATE/BIRTHDAY_POINTS/FREE_UPGRADE',
  benefit_name  VARCHAR(128) NOT NULL,
  benefit_value VARCHAR(255) NULL COMMENT '权益参数（如积分倍率 1.5、生日券模板 ID）',
  status        VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
  create_time   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_level_benefit (brand_id, level_id, benefit_code),
  KEY idx_lb_level (level_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员等级营销权益';

-- ---------- 营销活动 ----------
CREATE TABLE mkt_activity (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id       BIGINT UNSIGNED NOT NULL,
  activity_no    VARCHAR(64) NOT NULL,
  activity_name  VARCHAR(128) NOT NULL,
  activity_type  VARCHAR(32) NOT NULL COMMENT 'NEW_MEMBER_GIFT/BIRTHDAY/FESTIVAL/CONTINUOUS_SIGN/FULL_GIFT/POINTS_MALL/SLEEPING_RECALL/STORE_SPECIAL',
  scope_type     VARCHAR(16) NOT NULL DEFAULT 'ALL' COMMENT 'ALL/STORE',
  start_time     DATETIME(3) NOT NULL,
  end_time       DATETIME(3) NOT NULL,
  config_json    TEXT NULL COMMENT '活动参数（赠券模板 ID、满赠门槛、积分倍率等）',
  total_quota    INT NOT NULL DEFAULT 0 COMMENT '活动总名额，0=不限',
  used_quota     INT NOT NULL DEFAULT 0 COMMENT '已使用名额',
  per_member_limit INT NOT NULL DEFAULT 1,
  priority       INT NOT NULL DEFAULT 0 COMMENT '同类型活动优先级，大者优先',
  status         VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ENABLED/DISABLED/EXPIRED',
  description    VARCHAR(500) NULL,
  create_time    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted        TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity_no (activity_no),
  KEY idx_activity_brand_status (brand_id, status, start_time, end_time),
  KEY idx_activity_type (activity_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='营销活动';

-- ---------- 活动适用门店 ----------
CREATE TABLE mkt_activity_store (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id    BIGINT UNSIGNED NOT NULL,
  activity_id BIGINT UNSIGNED NOT NULL,
  store_id    BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity_store (activity_id, store_id),
  KEY idx_as_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动适用门店';

-- ---------- 活动参与记录（幂等 + 名额计数）----------
CREATE TABLE mkt_activity_record (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id    BIGINT UNSIGNED NOT NULL,
  activity_id BIGINT UNSIGNED NOT NULL,
  member_id   BIGINT UNSIGNED NOT NULL,
  biz_no      VARCHAR(64) NULL COMMENT '关联业务单号（订单号等）',
  reward_json TEXT NULL COMMENT '本次发放的奖励快照',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  -- 同一活动同一会员同一业务单只能参与一次
  UNIQUE KEY uk_activity_member_biz (activity_id, member_id, biz_no),
  KEY idx_ar_member (member_id, create_time),
  KEY idx_ar_activity (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动参与记录';

-- ---------- 积分商城商品 ----------
CREATE TABLE mkt_points_goods (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id       BIGINT UNSIGNED NOT NULL,
  goods_no       VARCHAR(64) NOT NULL,
  goods_name     VARCHAR(128) NOT NULL,
  goods_type     VARCHAR(32) NOT NULL COMMENT 'COUPON/PHYSICAL/POINTS_PACK',
  image_url      VARCHAR(255) NULL,
  points_price   BIGINT NOT NULL COMMENT '兑换所需积分',
  stock          INT NOT NULL DEFAULT 0 COMMENT '库存，-1=不限',
  template_id    BIGINT UNSIGNED NULL COMMENT 'goods_type=COUPON 时对应的券模板',
  exchange_limit INT NOT NULL DEFAULT 1 COMMENT '每人限兑',
  sort           INT NOT NULL DEFAULT 0,
  status         VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
  create_time    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted        TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_goods_no (goods_no),
  KEY idx_goods_brand_status (brand_id, status, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分商城商品';

-- ---------- 积分兑换记录 ----------
CREATE TABLE mkt_points_exchange (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id     BIGINT UNSIGNED NOT NULL,
  exchange_no  VARCHAR(64) NOT NULL,
  member_id    BIGINT UNSIGNED NOT NULL,
  goods_id     BIGINT UNSIGNED NOT NULL,
  goods_name   VARCHAR(128) NOT NULL,
  points_cost  BIGINT NOT NULL,
  coupon_id    BIGINT UNSIGNED NULL COMMENT '兑换券类商品后生成的券实例',
  status       VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
  create_time  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_exchange_no (exchange_no),
  KEY idx_pe_member (member_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分兑换记录';

-- ============================================================
-- 初始数据：默认积分规则（每 1 元得 1 积分，签到 5 分，365 天有效）
-- ============================================================
INSERT INTO mkt_points_rule (brand_id, rule_code, rule_name, amount_per_point, sign_in_points,
                             sign_in_continuous_max, sign_in_bonus, expire_days, status)
VALUES (1, 'ORDER_EARN', '消费得积分', 100, 5, 7, 2, 365, 'ENABLED')
ON DUPLICATE KEY UPDATE update_time = CURRENT_TIMESTAMP(3);
