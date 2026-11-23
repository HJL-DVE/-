-- 授予店长「主动查单」权限（幂等，可重复执行）
-- 目标库：iam_db
-- 背景：支付流水页新增手动查单入口，店长需要该权限核实支付状态

INSERT INTO sys_role_permission (role_id, permission_id, create_time)
SELECT r.id, p.id, NOW()
FROM sys_role r, sys_permission p
WHERE r.role_code = 'STORE_MANAGER' AND p.perm_code = 'payment:query'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
