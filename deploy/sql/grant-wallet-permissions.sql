-- 授予店长与收银员「储值退款」「余额人工调整」权限（幂等，可重复执行）
-- 目标库：iam_db
-- 背景：店长需能退本店储值、调整余额；收银员需能调整余额（退款权限此前已有）

-- 店长：储值退款（充值退卡）
INSERT INTO sys_role_permission (role_id, permission_id, create_time)
SELECT r.id, p.id, NOW()
FROM sys_role r, sys_permission p
WHERE r.role_code = 'STORE_MANAGER' AND p.perm_code = 'wallet:refund'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- 店长：余额人工调整
INSERT INTO sys_role_permission (role_id, permission_id, create_time)
SELECT r.id, p.id, NOW()
FROM sys_role r, sys_permission p
WHERE r.role_code = 'STORE_MANAGER' AND p.perm_code = 'wallet:adjust'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- 收银员：余额人工调整（wallet:refund/menu/recharge:list 此前已具备）
INSERT INTO sys_role_permission (role_id, permission_id, create_time)
SELECT r.id, p.id, NOW()
FROM sys_role r, sys_permission p
WHERE r.role_code = 'CASHIER' AND p.perm_code = 'wallet:adjust'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
