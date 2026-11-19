-- ============================================================
-- V7: 储值业务种子数据（充值方案 / 钱包 / 新增权限点）
-- ============================================================

-- ---------- 充值方案 ----------
INSERT INTO wal_recharge_plan (id, brand_id, plan_code, plan_name, recharge_amount, gift_amount, gift_expire_days, min_level_id, custom_amount_enabled, per_member_limit, sort, status, start_time, end_time, remark) VALUES
(1, 1, 'RC100', '充 100 送 10',  10000, 1000, 365, NULL, 0, 0, 1, 'ENABLED', NULL, NULL, '基础充值方案'),
(2, 1, 'RC200', '充 200 送 30',  20000, 3000, 365, NULL, 0, 0, 2, 'ENABLED', NULL, NULL, '人气充值方案'),
(3, 1, 'RC500', '充 500 送 100', 50000, 10000, 365, NULL, 0, 0, 3, 'ENABLED', NULL, NULL, '高性价比'),
(4, 1, 'RC1000','充 1000 送 250',100000, 25000, 365, NULL, 0, 5, 4, 'ENABLED', NULL, NULL, '每人限 5 次'),
(5, 1, 'RCCUSTOM', '自定义充值', 0, 0, 365, NULL, 1, 0, 5, 'ENABLED', NULL, NULL, '自定义金额 10-5000 元'),
(6, 1, 'RCVIP', 'VIP 专享 充 300 送 60', 30000, 6000, 365, 3, 0, 3, 6, 'ENABLED', NULL, NULL, '金卡及以上会员专享');

UPDATE wal_recharge_plan SET min_custom_amount = 1000, max_custom_amount = 500000 WHERE plan_code = 'RCCUSTOM';

-- ---------- 充值方案参与门店 ----------
-- 方案 1-4 全部门店（不配置记录）；方案 6 仅西湖旗舰店与滨江店
INSERT INTO wal_recharge_plan_store (plan_id, store_id) VALUES (6, 1), (6, 2);

-- ---------- 演示会员钱包 ----------
INSERT INTO wal_wallet (brand_id, member_id, principal_balance, gift_balance, total_principal_in, total_gift_in, status) VALUES
(1, 1, 50000, 8000, 50000, 8000, 'NORMAL'),
(1, 2, 200000, 30000, 200000, 30000, 'NORMAL'),
(1, 3, 0, 0, 0, 0, 'NORMAL');

-- ---------- 新增权限点（储值 / 订单 / 支付） ----------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, route_path, component, icon, sort, visible) VALUES
(80, 0, 'wallet:menu', '储值管理', 'MENU', '/wallet', 'Layout', 'Wallet', 50, 1),
(81, 80, 'wallet:plan:list', '充值方案', 'MENU', '/wallet/plan', 'wallet/plan', 'List', 1, 1),
(82, 80, 'wallet:plan:create', '新增充值方案', 'BUTTON', NULL, NULL, NULL, 1, 0),
(83, 80, 'wallet:plan:update', '编辑充值方案', 'BUTTON', NULL, NULL, NULL, 2, 0),
(84, 80, 'wallet:plan:delete', '删除充值方案', 'BUTTON', NULL, NULL, NULL, 3, 0),
(85, 80, 'wallet:account:list', '会员钱包', 'MENU', '/wallet/account', 'wallet/account', 'CreditCard', 2, 1),
(86, 80, 'wallet:txn:list', '资金流水', 'MENU', '/wallet/transaction', 'wallet/transaction', 'Tickets', 3, 1),
(87, 80, 'wallet:recharge:list', '充值记录', 'MENU', '/wallet/recharge', 'wallet/recharge', 'Money', 4, 1),
(88, 80, 'wallet:refund', '储值退款', 'BUTTON', NULL, NULL, NULL, 4, 0),
(89, 80, 'wallet:adjust', '余额人工调整', 'BUTTON', NULL, NULL, NULL, 5, 0),

(90, 0, 'order:menu', '订单管理', 'MENU', '/order', 'Layout', 'Document', 60, 1),
(91, 90, 'order:list', '订单列表', 'MENU', '/order/list', 'order/list', 'List', 1, 1),
(92, 90, 'order:detail', '订单详情', 'BUTTON', NULL, NULL, NULL, 1, 0),
(93, 90, 'order:create', '创建订单', 'BUTTON', NULL, NULL, NULL, 2, 0),
(94, 90, 'order:cancel', '取消订单', 'BUTTON', NULL, NULL, NULL, 3, 0),
(95, 90, 'order:refund', '订单退款', 'BUTTON', NULL, NULL, NULL, 4, 0),
(96, 90, 'order:pay:balance', '余额支付', 'BUTTON', NULL, NULL, NULL, 5, 0),
(97, 90, 'order:statistics', '订单统计', 'MENU', '/order/statistics', 'order/statistics', 'DataLine', 2, 1),

(100, 0, 'payment:menu', '支付管理', 'MENU', '/payment', 'Layout', 'CreditCard', 70, 1),
(101, 100, 'payment:order:list', '支付流水', 'MENU', '/payment/order', 'payment/order', 'Tickets', 1, 1),
(102, 100, 'payment:refund:list', '退款管理', 'MENU', '/payment/refund', 'payment/refund', 'RefreshLeft', 2, 1),
(103, 100, 'payment:notify:list', '通知记录', 'MENU', '/payment/notify', 'payment/notify', 'Bell', 3, 1),
(104, 100, 'payment:query', '主动查单', 'BUTTON', NULL, NULL, NULL, 1, 0),
(105, 100, 'payment:reconcile', '对账', 'BUTTON', NULL, NULL, NULL, 2, 0);

-- ---------- 给总部与超管授予新权限 ----------
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE id >= 80 AND id NOT IN (SELECT permission_id FROM sys_role_permission WHERE role_id = 1);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 2, id FROM sys_permission WHERE id >= 80 AND id NOT IN (SELECT permission_id FROM sys_role_permission WHERE role_id = 2);

-- 店长：订单与储值查看/收银（不含人工调整与对账）
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(3, 80), (3, 81), (3, 85), (3, 86), (3, 87),
(3, 90), (3, 91), (3, 92), (3, 93), (3, 94), (3, 95), (3, 96), (3, 97),
(3, 100), (3, 101), (3, 102);

-- 收银员：仅订单收银与储值查看
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(4, 80), (4, 85), (4, 86), (4, 87),
(4, 90), (4, 91), (4, 92), (4, 93), (4, 96);
