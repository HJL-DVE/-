-- 删除品牌级的「赠送金额有效期」字段。
--
-- 该字段只被品牌 CRUD 存取，充值入账时从未读取；真正生效的是
-- 充值方案 wal_recharge_plan.gift_expire_days（→ 充值订单 → 赠送流水 gift_expire_time），
-- 过期核销由 wallet-service 的 GiftExpiryScheduler 执行。
--
-- 注意：不要修改 V1__init.sql（会破坏已执行迁移的 checksum），用新增迁移删除列。
ALTER TABLE sys_brand DROP COLUMN gift_expire_days;
