INSERT INTO `permission` (`id`, `name`, `value`, `pid`)
VALUES
	(57, '文件管理', 'file', 0),
	(58, '文件列表', 'file:list', 57),
	(59, '文件删除', 'file:delete', 57),
	(60, '话题彻底删除', 'topic:physical_delete', 2);

INSERT INTO `role_permission` (`role_id`, `permission_id`)
VALUES
	(1, 57),
	(1, 58),
	(1, 59),
	(1, 60);
