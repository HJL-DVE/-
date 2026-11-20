-- ============================================================
-- marketing-service V5: 补齐签到积分规则
--
-- 缺陷说明：V2 只插入了 ORDER_EARN 一条规则，
-- 而 signIn() 查的是 rule_code='SIGN_IN'，查不到就落到默认值 0，
-- 结果「签到成功但拿 0 积分」——看起来像功能没做，实际是数据缺失。
--
-- 这里补一条独立的 SIGN_IN 规则；若已存在则不覆盖运维改过的值。
-- ============================================================

INSERT INTO mkt_points_rule (brand_id, rule_code, rule_name, amount_per_point, sign_in_points,
                             sign_in_continuous_max, sign_in_bonus, expire_days, status, deleted)
SELECT 1, 'SIGN_IN', '签到得积分', 100, 5, 7, 2, 365, 'ENABLED', 0
WHERE NOT EXISTS (
  SELECT 1 FROM mkt_points_rule WHERE brand_id = 1 AND rule_code = 'SIGN_IN'
);
