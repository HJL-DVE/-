-- 阶段九：营销活动种子数据（8 类活动，幂等可重跑）
-- 活动有效期用相对时间，避免写死日期导致过期
INSERT INTO mkt_activity
  (brand_id, activity_no, activity_name, activity_type, scope_type, start_time, end_time,
   config_json, total_quota, used_quota, per_member_limit, priority, status, description, deleted, create_time, update_time)
SELECT 1, CONCAT('ACT-SEED-', t.activity_type), t.activity_name, t.activity_type, t.scope_type,
       DATE_SUB(NOW(3), INTERVAL 1 DAY), DATE_ADD(NOW(3), INTERVAL 365 DAY),
       t.config_json, t.total_quota, 0, t.per_member_limit, t.priority, 'ENABLED', t.description, 0, NOW(3), NOW(3)
FROM (
  SELECT 'NEW_MEMBER_GIFT' AS activity_type, '新人见面礼' AS activity_name, 'ALL' AS scope_type,
         '{"couponTemplateIds":[1],"points":100,"description":"注册即送满50减10券+100积分"}' AS config_json,
         0 AS total_quota, 1 AS per_member_limit, 10 AS priority, '新人注册自动发放见面礼' AS description
  UNION ALL SELECT 'BIRTHDAY', '生日专享福利', 'ALL',
         '{"couponTemplateIds":[13],"points":200,"description":"生日当月送生日券+200积分"}', 0, 1, 9, '生日当月可领专属券和积分'
  UNION ALL SELECT 'FESTIVAL', '国庆黄金周', 'ALL',
         '{"couponTemplateIds":[1],"points":50,"description":"节日下单送券+50积分"}', 500, 3, 8, '节日限时活动'
  UNION ALL SELECT 'CONTINUOUS_SIGN', '连续签到7天礼', 'ALL',
         '{"continuousDays":7,"points":150,"couponTemplateIds":[1],"description":"连续签到7天送150积分+满减券"}', 0, 1, 7, '连续签到达标自动发奖'
  UNION ALL SELECT 'FULL_GIFT', '满30送5元券', 'ALL',
         '{"thresholdAmount":3000,"couponTemplateIds":[2],"points":30,"description":"满30送5元券+30积分"}', 1000, 5, 10, '订单满额自动赠送'
  UNION ALL SELECT 'POINTS_MALL', '会员积分商城', 'ALL',
         '{"description":"积分兑换商品长期开放"}', 0, 0, 5, '长期开放的积分兑换'
  UNION ALL SELECT 'STORE_SPECIAL', '西湖店专属优惠', 'ALL',
         '{"thresholdAmount":2000,"points":20,"description":"满20元送20积分"}', 300, 2, 6, '门店专属活动'
  UNION ALL SELECT 'SLEEPING_RECALL', '沉睡会员召回', 'ALL',
         '{"sleepingDays":30,"couponTemplateIds":[2],"points":50,"description":"30天未消费送5元券+50积分"}', 200, 1, 4, '沉睡会员定向召回'
) t
WHERE NOT EXISTS (SELECT 1 FROM (SELECT activity_type FROM mkt_activity WHERE deleted=0) e WHERE e.activity_type=t.activity_type);