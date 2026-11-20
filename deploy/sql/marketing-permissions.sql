-- ============================================================
-- 营销（marketing-service）权限点与菜单 —— 在 **iam_db** 执行
--
-- 为什么单独成文件：sys_permission 属于 iam_db（auth-service 的库），
-- marketing-service 的 Flyway 只迁移 marketing_db，不能跨库写。
--
-- 幂等：全部用 WHERE NOT EXISTS 保护，可重复执行。
-- 执行：docker exec -i teaclub-mysql mysql -uroot -p*** iam_db < deploy/sql/marketing-permissions.sql
-- ============================================================

SET NAMES utf8mb4;

-- ---------- 1. 一级菜单 ----------
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, route_path, component, icon, sort, visible, status)
SELECT 0, 'marketing:menu', '营销管理', 'MENU', '/marketing', 'Layout', 'Present', 40, 1, 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'marketing:menu');

SET @menu := (SELECT id FROM sys_permission WHERE perm_code = 'marketing:menu' LIMIT 1);

-- ---------- 2. 二级菜单 ----------
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, route_path, component, icon, sort, visible, status)
SELECT @menu, 'marketing:coupon:list', '优惠券管理', 'MENU', '/marketing/coupon', 'marketing/coupon', 'Ticket', 1, 1, 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'marketing:coupon:list');

INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, route_path, component, icon, sort, visible, status)
SELECT @menu, 'marketing:points:rule', '积分规则', 'MENU', '/marketing/points-rule', 'marketing/pointsRule', 'SetUp', 2, 1, 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'marketing:points:rule');

INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, route_path, component, icon, sort, visible, status)
SELECT @menu, 'marketing:points:list', '积分流水', 'MENU', '/marketing/points', 'marketing/points', 'DataLine', 3, 1, 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'marketing:points:list');

INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, route_path, component, icon, sort, visible, status)
SELECT @menu, 'marketing:activity:list', '营销活动', 'MENU', '/marketing/activity', 'marketing/activity', 'Calendar', 4, 1, 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'marketing:activity:list');

INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, route_path, component, icon, sort, visible, status)
SELECT @menu, 'marketing:activity:stat', '活动统计', 'MENU', '/marketing/stat', 'marketing/stat', 'TrendCharts', 5, 1, 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'marketing:activity:stat');

INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, route_path, component, icon, sort, visible, status)
SELECT @menu, 'marketing:level:list', '会员等级', 'MENU', '/marketing/level', 'marketing/level', 'Medal', 6, 1, 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'marketing:level:list');

SET @couponMenu := (SELECT id FROM sys_permission WHERE perm_code = 'marketing:coupon:list' LIMIT 1);
SET @pointsMenu := (SELECT id FROM sys_permission WHERE perm_code = 'marketing:points:list' LIMIT 1);
SET @activityMenu := (SELECT id FROM sys_permission WHERE perm_code = 'marketing:activity:list' LIMIT 1);
SET @levelMenu := (SELECT id FROM sys_permission WHERE perm_code = 'marketing:level:list' LIMIT 1);

-- ---------- 3. 按钮权限 ----------
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, route_path, component, icon, sort, visible, status)
SELECT @couponMenu, 'marketing:coupon:create', '新增券模板', 'BUTTON', NULL, NULL, NULL, 1, 0, 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'marketing:coupon:create');

INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, route_path, component, icon, sort, visible, status)
SELECT @couponMenu, 'marketing:coupon:update', '编辑券模板', 'BUTTON', NULL, NULL, NULL, 2, 0, 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'marketing:coupon:update');

INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, route_path, component, icon, sort, visible, status)
SELECT @couponMenu, 'marketing:coupon:delete', '删除券模板', 'BUTTON', NULL, NULL, NULL, 3, 0, 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'marketing:coupon:delete');

INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, route_path, component, icon, sort, visible, status)
SELECT @couponMenu, 'marketing:coupon:grant', '发放优惠券', 'BUTTON', NULL, NULL, NULL, 4, 0, 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'marketing:coupon:grant');

INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, route_path, component, icon, sort, visible, status)
SELECT @pointsMenu, 'marketing:points:adjust', '积分人工调整', 'BUTTON', NULL, NULL, NULL, 1, 0, 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'marketing:points:adjust');

INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, route_path, component, icon, sort, visible, status)
SELECT @pointsMenu, 'marketing:points:rule:update', '编辑积分规则', 'BUTTON', NULL, NULL, NULL, 2, 0, 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'marketing:points:rule:update');

INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, route_path, component, icon, sort, visible, status)
SELECT @activityMenu, 'marketing:activity:create', '新增活动', 'BUTTON', NULL, NULL, NULL, 1, 0, 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'marketing:activity:create');

INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, route_path, component, icon, sort, visible, status)
SELECT @activityMenu, 'marketing:activity:update', '编辑活动', 'BUTTON', NULL, NULL, NULL, 2, 0, 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'marketing:activity:update');

INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, route_path, component, icon, sort, visible, status)
SELECT @activityMenu, 'marketing:activity:delete', '删除活动', 'BUTTON', NULL, NULL, NULL, 3, 0, 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'marketing:activity:delete');

INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, route_path, component, icon, sort, visible, status)
SELECT @levelMenu, 'marketing:level:update', '编辑等级权益', 'BUTTON', NULL, NULL, NULL, 1, 0, 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'marketing:level:update');

-- ---------- 4. 授予 SUPER_ADMIN（role id=1） ----------
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, p.id FROM sys_permission p
WHERE p.perm_code LIKE 'marketing:%'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = 1 AND rp.permission_id = p.id
  );

-- ---------- 5. 校验 ----------
SELECT '=== 营销权限点 ===' AS info;
SELECT perm_code, perm_name, perm_type FROM sys_permission WHERE perm_code LIKE 'marketing:%' ORDER BY sort;
SELECT '=== SUPER_ADMIN 已授予 ===' AS info;
SELECT COUNT(*) AS granted FROM sys_role_permission rp
JOIN sys_permission p ON p.id = rp.permission_id
WHERE rp.role_id = 1 AND p.perm_code LIKE 'marketing:%';
