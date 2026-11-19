-- ============================================================
-- V8: 为种子钱包补记期初流水
--
-- 背景：V7 直接给会员 1/2 写了初始钱包余额，但按资金不变量
--   principal_balance = SUM(principal_change)
--   gift_balance      = SUM(gift_change)
-- 没有流水的余额属于"凭空出现"，会导致对账任务永远报错。
-- 本迁移为既有余额补一条 OPENING 期初入账流水，使不变量成立。
-- ============================================================

INSERT INTO wal_wallet_transaction
  (brand_id, wallet_id, member_id, store_id, txn_no, biz_type, biz_no,
   principal_change, principal_before, principal_after,
   gift_change, gift_before, gift_after,
   amount_paid, gift_amount, remark, operator_type)
SELECT
  w.brand_id,
  w.id,
  w.member_id,
  1,
  CONCAT('WTSEED', LPAD(w.id, 6, '0')),
  'ADMIN_ADJUST',
  CONCAT('SEED-OPENING-', w.id),
  w.principal_balance, 0, w.principal_balance,
  w.gift_balance, 0, w.gift_balance,
  w.principal_balance, w.gift_balance,
  '种子数据期初余额（使余额与流水一致）',
  'SYSTEM'
FROM wal_wallet w
WHERE w.principal_balance > 0 OR w.gift_balance > 0;
