-- 订单积分抵扣：记录本单实际使用的积分数（1 积分 = 1 分钱）。
-- 下单试算只查余额不扣；支付成功按订单号幂等扣减；取消订单不扣（积分尚未扣除）。
ALTER TABLE oms_order
  ADD COLUMN use_points BIGINT NOT NULL DEFAULT 0 COMMENT '本单实际使用积分（1积分=1分钱）' AFTER member_coupon_id;
