-- 阶段八：经营报表权限点（幂等，可重跑；对 iam_db 执行）
-- parent_id 不能为 NULL：顶级菜单用 0
INSERT INTO sys_permission (perm_code, perm_name, perm_type, parent_id, route_path, component, sort, visible, status, deleted)
SELECT 'report:view', '经营报表查看', 'MENU', 0, '/report', 'report/index', 60, 1, 'ENABLED', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'report:view');

INSERT INTO sys_permission (perm_code, perm_name, perm_type, parent_id, route_path, component, sort, visible, status, deleted)
SELECT c.code, c.name, 'MENU', (SELECT id FROM sys_permission WHERE perm_code='report:view'), c.path, c.comp, c.sort, 1, 'ENABLED', 0
FROM (
  SELECT 'report:dashboard' AS code, '经营驾驶舱' AS name, '/report/dashboard' AS path, 'report/dashboard' AS comp, 1 AS sort UNION ALL
  SELECT 'report:sales',    '销售分析',   '/report/sales',    'report/sales',    2 UNION ALL
  SELECT 'report:member',   '会员分析',   '/report/member',   'report/member',   3 UNION ALL
  SELECT 'report:recharge', '充值统计',   '/report/recharge', 'report/recharge', 4 UNION ALL
  SELECT 'report:payment',  '支付统计',   '/report/payment',  'report/payment',  5 UNION ALL
  SELECT 'report:refund',   '退款统计',   '/report/refund',   'report/refund',   6 UNION ALL
  SELECT 'report:product',  '商品销售分析', '/report/product',  'report/product',  7 UNION ALL
  SELECT 'report:marketing','营销数据分析', '/report/marketing','report/marketing', 8
) c
WHERE NOT EXISTS (SELECT 1 FROM sys_permission p WHERE p.perm_code = c.code);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r
JOIN sys_permission p ON p.perm_code IN ('report:view','report:dashboard','report:sales','report:member','report:recharge','report:payment','report:refund','report:product','report:marketing')
WHERE r.id IN (1,2,3);

DELETE rp FROM sys_role_permission rp
JOIN sys_permission p ON p.id = rp.permission_id
WHERE rp.role_id = 4 AND p.perm_code LIKE 'report:%';