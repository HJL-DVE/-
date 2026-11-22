-- 删除未生效的「钱包跨店使用」开关。
--
-- 背景：会员钱包属于品牌（brand_id + member_id），天生可跨门店消费；
-- 该字段仅被品牌 CRUD 存取，wallet-service / order-service 扣款时从未读取，
-- 保留会给使用者造成「关掉就能禁止跨店」的误解。
--
-- 注意：不要修改 V1__init.sql（会破坏已执行迁移的 checksum），用新增迁移删除列。
ALTER TABLE sys_brand DROP COLUMN wallet_cross_store_enabled;
