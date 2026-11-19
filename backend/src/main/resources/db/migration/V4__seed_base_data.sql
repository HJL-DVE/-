-- ============================================================
-- V4: 基础种子数据（品牌 / 门店 / 权限点 / 角色 / 账号 / 会员等级 / 商品）
-- 说明：演示账号初始密码统一为 TeaClub@123（BCrypt 存储）
-- ============================================================

-- ---------- 品牌 ----------
INSERT INTO sys_brand (id, brand_code, brand_name, contact_phone, wallet_cross_store_enabled, wallet_deduct_order, gift_expire_days, status)
VALUES (1, 'TEACLUB', 'TeaClub 茶研社', '400-800-1234', 1, 'GIFT_FIRST', 365, 'ENABLED');

-- ---------- 门店 ----------
INSERT INTO sys_store (id, brand_id, store_code, store_name, province, city, district, address, phone, manager_name, business_hours, open_date, status) VALUES
(1, 1, 'S001', 'TeaClub·西湖旗舰店', '浙江省', '杭州市', '西湖区', '延安路 288 号 1 层', '0571-88001001', '王店长', '09:00-22:00', '2024-03-01', 'OPEN'),
(2, 1, 'S002', 'TeaClub·滨江店',     '浙江省', '杭州市', '滨江区', '江南大道 588 号 B1-12', '0571-88001002', '李店长', '10:00-22:30', '2024-06-15', 'OPEN'),
(3, 1, 'S003', 'TeaClub·城西银泰店', '浙江省', '杭州市', '拱墅区', '丰潭路 380 号 3 层', '0571-88001003', '张店长', '10:00-21:30', '2025-01-10', 'OPEN');

-- ---------- 权限点（菜单 + 按钮） ----------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, route_path, component, icon, sort, visible) VALUES
-- 仪表盘
(1, 0, 'dashboard:view', '工作台', 'MENU', '/dashboard', 'dashboard/index', 'Odometer', 10, 1),

-- 门店管理
(10, 0, 'store:menu', '门店管理', 'MENU', '/store', 'Layout', 'Shop', 20, 1),
(11, 10, 'store:list', '门店列表', 'MENU', '/store/list', 'store/list', 'List', 1, 1),
(12, 10, 'store:create', '新增门店', 'BUTTON', NULL, NULL, NULL, 1, 0),
(13, 10, 'store:update', '编辑门店', 'BUTTON', NULL, NULL, NULL, 2, 0),
(14, 10, 'store:delete', '删除门店', 'BUTTON', NULL, NULL, NULL, 3, 0),

-- 品牌
(20, 0, 'brand:menu', '品牌管理', 'MENU', '/brand', 'Layout', 'Postcard', 25, 1),
(21, 20, 'brand:view', '品牌信息', 'MENU', '/brand/detail', 'brand/detail', 'Document', 1, 1),
(22, 20, 'brand:update', '编辑品牌', 'BUTTON', NULL, NULL, NULL, 1, 0),

-- 会员管理
(30, 0, 'member:menu', '会员管理', 'MENU', '/member', 'Layout', 'User', 30, 1),
(31, 30, 'member:list', '会员列表', 'MENU', '/member/list', 'member/list', 'List', 1, 1),
(32, 30, 'member:detail', '会员详情', 'BUTTON', NULL, NULL, NULL, 1, 0),
(33, 30, 'member:update', '编辑会员', 'BUTTON', NULL, NULL, NULL, 2, 0),
(34, 30, 'member:status', '冻结/解冻', 'BUTTON', NULL, NULL, NULL, 3, 0),
(35, 30, 'member:delete', '删除会员', 'BUTTON', NULL, NULL, NULL, 4, 0),
(36, 30, 'member:level:list', '会员等级', 'MENU', '/member/level', 'member/level', 'Medal', 2, 1),
(37, 30, 'member:level:create', '新增等级', 'BUTTON', NULL, NULL, NULL, 5, 0),
(38, 30, 'member:level:update', '编辑等级', 'BUTTON', NULL, NULL, NULL, 6, 0),
(39, 30, 'member:level:delete', '删除等级', 'BUTTON', NULL, NULL, NULL, 7, 0),

-- 商品管理
(40, 0, 'product:menu', '商品管理', 'MENU', '/product', 'Layout', 'Goods', 40, 1),
(41, 40, 'product:category:list', '商品分类', 'MENU', '/product/category', 'product/category', 'Menu', 1, 1),
(42, 40, 'product:category:create', '新增分类', 'BUTTON', NULL, NULL, NULL, 1, 0),
(43, 40, 'product:category:update', '编辑分类', 'BUTTON', NULL, NULL, NULL, 2, 0),
(44, 40, 'product:category:delete', '删除分类', 'BUTTON', NULL, NULL, NULL, 3, 0),
(45, 40, 'product:spu:list', '商品列表', 'MENU', '/product/spu', 'product/spu', 'Box', 2, 1),
(46, 40, 'product:spu:create', '新增商品', 'BUTTON', NULL, NULL, NULL, 4, 0),
(47, 40, 'product:spu:update', '编辑商品', 'BUTTON', NULL, NULL, NULL, 5, 0),
(48, 40, 'product:spu:delete', '删除商品', 'BUTTON', NULL, NULL, NULL, 6, 0),
(49, 40, 'product:store:list', '门店商品', 'MENU', '/product/store', 'product/store', 'Shop', 3, 1),
(50, 40, 'product:store:update', '门店商品配置', 'BUTTON', NULL, NULL, NULL, 7, 0),

-- 系统管理
(60, 0, 'system:menu', '系统管理', 'MENU', '/system', 'Layout', 'Setting', 90, 1),
(61, 60, 'system:user:list', '员工管理', 'MENU', '/system/user', 'system/user', 'UserFilled', 1, 1),
(62, 60, 'system:user:create', '新增员工', 'BUTTON', NULL, NULL, NULL, 1, 0),
(63, 60, 'system:user:update', '编辑员工', 'BUTTON', NULL, NULL, NULL, 2, 0),
(64, 60, 'system:user:delete', '删除员工', 'BUTTON', NULL, NULL, NULL, 3, 0),
(65, 60, 'system:user:reset', '重置密码', 'BUTTON', NULL, NULL, NULL, 4, 0),
(66, 60, 'system:user:grant', '门店授权', 'BUTTON', NULL, NULL, NULL, 5, 0),
(67, 60, 'system:role:list', '角色管理', 'MENU', '/system/role', 'system/role', 'Key', 2, 1),
(68, 60, 'system:role:create', '新增角色', 'BUTTON', NULL, NULL, NULL, 6, 0),
(69, 60, 'system:role:update', '编辑角色', 'BUTTON', NULL, NULL, NULL, 7, 0),
(70, 60, 'system:role:delete', '删除角色', 'BUTTON', NULL, NULL, NULL, 8, 0),
(71, 60, 'system:role:grant', '角色授权', 'BUTTON', NULL, NULL, NULL, 9, 0),
(72, 60, 'system:permission:list', '权限管理', 'MENU', '/system/permission', 'system/permission', 'Grid', 3, 1),
(73, 60, 'system:log:list', '操作日志', 'MENU', '/system/log', 'system/log', 'Tickets', 4, 1);

-- ---------- 角色 ----------
INSERT INTO sys_role (id, brand_id, role_code, role_name, data_scope, is_builtin, sort, status, remark) VALUES
(1, 1, 'SUPER_ADMIN',   '超级管理员',  'ALL',   1, 1,  'ENABLED', '拥有全部权限，不受门店数据范围限制'),
(2, 1, 'HQ_ADMIN',      '总部管理员',  'ALL',   1, 2,  'ENABLED', '管理全部门店与全品牌业务'),
(3, 1, 'STORE_MANAGER', '店长',        'STORE', 1, 3,  'ENABLED', '仅管理被授权门店'),
(4, 1, 'CASHIER',       '收银员',      'STORE', 1, 4,  'ENABLED', '仅使用授权门店的收银与查询功能');

-- ---------- 角色权限 ----------
-- 超级管理员 / 总部管理员：全部权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission;
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 2, id FROM sys_permission;

-- 店长：门店查看、会员、商品查看与门店商品配置、工作台
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(3, 1),
(3, 10), (3, 11),
(3, 30), (3, 31), (3, 32), (3, 36),
(3, 40), (3, 41), (3, 45), (3, 49), (3, 50);

-- 收银员：工作台、会员查询、商品查看
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(4, 1),
(4, 30), (4, 31), (4, 32),
(4, 40), (4, 45);

-- ---------- 员工账号 ----------
-- 初始密码统一为 TeaClub@123
INSERT INTO sys_user (id, brand_id, username, password, real_name, phone, user_type, status) VALUES
(1, 1, 'admin',     '$2a$10$GQ8LFacPDaAkj7Hl/VnR..b5z0rJ17BoUNZd2UAMWRARweIeyywpe', '超级管理员', '13900000001', 'HQ',    'ENABLED'),
(2, 1, 'hqadmin',   '$2a$10$GQ8LFacPDaAkj7Hl/VnR..b5z0rJ17BoUNZd2UAMWRARweIeyywpe', '总部管理员', '13900000002', 'HQ',    'ENABLED'),
(3, 1, 'mgr_xihu',  '$2a$10$GQ8LFacPDaAkj7Hl/VnR..b5z0rJ17BoUNZd2UAMWRARweIeyywpe', '王店长',     '13900000003', 'STORE', 'ENABLED'),
(4, 1, 'mgr_binjiang', '$2a$10$GQ8LFacPDaAkj7Hl/VnR..b5z0rJ17BoUNZd2UAMWRARweIeyywpe', '李店长', '13900000004', 'STORE', 'ENABLED'),
(5, 1, 'cashier01', '$2a$10$GQ8LFacPDaAkj7Hl/VnR..b5z0rJ17BoUNZd2UAMWRARweIeyywpe', '收银员小陈', '13900000005', 'STORE', 'ENABLED');

-- ---------- 角色绑定 ----------
INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 1), (2, 2), (3, 3), (4, 3), (5, 4);

-- ---------- 门店授权（数据权限） ----------
-- 总部账号不设门店授权（data_scope=ALL 时忽略此表）
INSERT INTO sys_user_store (user_id, store_id, is_default) VALUES
(3, 1, 1),   -- mgr_xihu    -> 西湖旗舰店
(4, 2, 1),   -- mgr_binjiang-> 滨江店
(5, 1, 1);   -- cashier01   -> 西湖旗舰店

-- ---------- 会员等级 ----------
INSERT INTO mem_member_level (id, brand_id, level_code, level_name, level_no, upgrade_type, upgrade_threshold, discount_rate, point_rate, status, benefits) VALUES
(1, 1, 'NORMAL',  '普通会员', 1, 'CONSUME_AMOUNT', 0,     10000, 100, 'ENABLED', '基础积分'),
(2, 1, 'SILVER',  '银卡会员', 2, 'CONSUME_AMOUNT', 50000,  9800, 120, 'ENABLED', '9.8折 + 1.2倍积分'),
(3, 1, 'GOLD',    '金卡会员', 3, 'CONSUME_AMOUNT', 150000, 9500, 150, 'ENABLED', '9.5折 + 1.5倍积分'),
(4, 1, 'BLACK',   '黑金会员', 4, 'CONSUME_AMOUNT', 500000, 9000, 200, 'ENABLED', '9折 + 2倍积分 + 专属客服');

-- ---------- 演示会员 ----------
INSERT INTO mem_member (id, brand_id, member_no, phone, nickname, gender, level_id, register_store_id, total_consume_amount, total_recharge_amount, total_order_count, points, status) VALUES
(1, 1, 'M20250001', '13800000001', '张小明', 'MALE',   2, 1, 68000,  50000, 12, 816,  'NORMAL'),
(2, 1, 'M20250002', '13800000002', '李小雨', 'FEMALE', 3, 2, 168000, 200000, 25, 2520, 'NORMAL'),
(3, 1, 'M20250003', '13800000003', '王大力', 'MALE',   1, 1, 12000,  0,     3,  120,  'NORMAL');

-- ---------- 商品分类 ----------
INSERT INTO prd_category (id, brand_id, parent_id, category_name, sort, status) VALUES
(1, 1, 0, '经典奶茶', 1, 'ENABLED'),
(2, 1, 0, '鲜果茶',   2, 'ENABLED'),
(3, 1, 0, '咖啡',     3, 'ENABLED'),
(4, 1, 0, '小料',     4, 'ENABLED');

-- ---------- 商品 SPU ----------
INSERT INTO prd_spu (id, brand_id, category_id, spu_code, spu_name, subtitle, base_price, min_price, unit, has_sku, sort, status) VALUES
(1, 1, 1, 'SPU001', '招牌珍珠奶茶', '现煮黑糖珍珠，醇香奶茶',   1800, 1500, '杯', 1, 1, 'ON_SALE'),
(2, 1, 1, 'SPU002', '经典奶茶',     '丝滑顺口，经典之选',       1400, 1200, '杯', 1, 2, 'ON_SALE'),
(3, 1, 2, 'SPU003', '芝士葡萄',     '当季鲜葡萄配芝士奶盖',     2200, 1800, '杯', 1, 3, 'ON_SALE'),
(4, 1, 2, 'SPU004', '满杯百香果',   '酸甜开胃，果肉满满',       1600, 1300, '杯', 1, 4, 'ON_SALE'),
(5, 1, 3, 'SPU005', '生椰拿铁',     '椰香浓郁，丝滑咖啡',       2000, 1600, '杯', 1, 5, 'ON_SALE'),
(6, 1, 4, 'SPU006', '加料-珍珠',    'Q弹黑糖珍珠',              300,  200,  '份', 0, 6, 'ON_SALE');

-- ---------- 商品 SKU ----------
INSERT INTO prd_sku (id, brand_id, spu_id, sku_code, sku_name, spec_json, price, cost_price, sort, status) VALUES
(1,  1, 1, 'SKU00101', '招牌珍珠奶茶/中杯', '{"杯型":"中杯","温度":"正常冰"}', 1800, 700, 1, 'ENABLED'),
(2,  1, 1, 'SKU00102', '招牌珍珠奶茶/大杯', '{"杯型":"大杯","温度":"正常冰"}', 2200, 850, 2, 'ENABLED'),
(3,  1, 2, 'SKU00201', '经典奶茶/中杯',     '{"杯型":"中杯","温度":"正常冰"}', 1400, 550, 1, 'ENABLED'),
(4,  1, 2, 'SKU00202', '经典奶茶/大杯',     '{"杯型":"大杯","温度":"正常冰"}', 1800, 700, 2, 'ENABLED'),
(5,  1, 3, 'SKU00301', '芝士葡萄/中杯',     '{"杯型":"中杯","温度":"少冰"}',   2200, 900, 1, 'ENABLED'),
(6,  1, 3, 'SKU00302', '芝士葡萄/大杯',     '{"杯型":"大杯","温度":"少冰"}',   2600, 1100, 2, 'ENABLED'),
(7,  1, 4, 'SKU00401', '满杯百香果/中杯',   '{"杯型":"中杯","温度":"正常冰"}', 1600, 600, 1, 'ENABLED'),
(8,  1, 4, 'SKU00402', '满杯百香果/大杯',   '{"杯型":"大杯","温度":"正常冰"}', 2000, 750, 2, 'ENABLED'),
(9,  1, 5, 'SKU00501', '生椰拿铁/中杯',     '{"杯型":"中杯","温度":"热"}',     2000, 800, 1, 'ENABLED'),
(10, 1, 5, 'SKU00502', '生椰拿铁/大杯',     '{"杯型":"大杯","温度":"热"}',     2400, 950, 2, 'ENABLED'),
(11, 1, 6, 'SKU00601', '加料-珍珠/份',      '{"规格":"单份"}',                300,  100, 1, 'ENABLED');

-- ---------- 门店商品（西湖旗舰店全量上架，滨江店部分，城西店未配置以便验证隔离） ----------
INSERT INTO prd_store_spu (brand_id, store_id, spu_id, is_on_sale, sort) VALUES
(1, 1, 1, 1, 1), (1, 1, 2, 1, 2), (1, 1, 3, 1, 3), (1, 1, 4, 1, 4), (1, 1, 5, 1, 5), (1, 1, 6, 1, 6),
(1, 2, 1, 1, 1), (1, 2, 2, 1, 2), (1, 2, 3, 1, 3);

-- 门店差异化定价：滨江店部分 SKU 价格上浮
INSERT INTO prd_store_sku (brand_id, store_id, sku_id, store_price, is_on_sale) VALUES
(1, 1, 1, 1800, 1), (1, 1, 2, 2200, 1), (1, 1, 3, 1400, 1), (1, 1, 4, 1800, 1),
(1, 1, 5, 2200, 1), (1, 1, 6, 2600, 1), (1, 1, 7, 1600, 1), (1, 1, 8, 2000, 1),
(1, 1, 9, 2000, 1), (1, 1, 10, 2400, 1), (1, 1, 11, 300, 1),
(1, 2, 1, 1900, 1), (1, 2, 2, 2300, 1), (1, 2, 3, 1500, 1), (1, 2, 4, 1900, 1),
(1, 2, 5, 2300, 1), (1, 2, 6, 2700, 1);