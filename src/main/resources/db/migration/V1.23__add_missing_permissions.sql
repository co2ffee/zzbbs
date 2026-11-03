-- 代码里的 @RequiresPermissions 要求了 role:add（新增角色）和 admin_user:delete（删除后台用户），
-- 但权限表里一直没有这两个权限，导致连超级管理员都无法新增角色、无法删除后台用户（接口返回“没有权限进行这次操作”）。
-- 这里补上这两个权限，并授予超级管理员（角色 id = 1）。已经存在时不会重复插入。

INSERT INTO `permission` (`name`, `value`, `pid`)
SELECT '角色创建', 'role:add', p.`id`
FROM `permission` p
WHERE p.`value` = 'role' AND p.`pid` = 0
  AND NOT EXISTS (SELECT 1 FROM `permission` x WHERE x.`value` = 'role:add');

INSERT INTO `permission` (`name`, `value`, `pid`)
SELECT '后台用户删除', 'admin_user:delete', p.`id`
FROM `permission` p
WHERE p.`value` = 'admin_user' AND p.`pid` = 0
  AND NOT EXISTS (SELECT 1 FROM `permission` x WHERE x.`value` = 'admin_user:delete');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 1, p.`id`
FROM `permission` p
WHERE p.`value` IN ('role:add', 'admin_user:delete')
  AND NOT EXISTS (SELECT 1 FROM `role_permission` rp WHERE rp.`role_id` = 1 AND rp.`permission_id` = p.`id`);
