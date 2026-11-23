-- 隐藏「权限管理」菜单（前端页面已移除）
-- 目标库：iam_db
-- 采用禁用而非删除：保留角色授权关联，日后可恢复
-- 恢复方式：UPDATE sys_permission SET visible=1, status='ENABLED' WHERE perm_code='system:permission:list';

UPDATE sys_permission
SET visible = 0, status = 'DISABLED'
WHERE perm_code = 'system:permission:list';
