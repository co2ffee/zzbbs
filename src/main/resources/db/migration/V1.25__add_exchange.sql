-- 积分兑换：商品（后台配置名称、图片、所需积分、库存）和兑换记录

CREATE TABLE `exchange_product` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '商品名称',
  `image` varchar(500) DEFAULT NULL COMMENT '商品图片地址',
  `description` varchar(500) DEFAULT NULL COMMENT '商品简介',
  `score` int(11) NOT NULL COMMENT '兑换需要的积分',
  `stock` int(11) NOT NULL DEFAULT -1 COMMENT '库存，-1 表示不限量',
  `sort` int(11) NOT NULL DEFAULT 0 COMMENT '排序，数字大的在前',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否上架',
  `in_time` datetime NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 兑换记录里保存商品名称、图片、积分的快照：商品以后改名、改价、删除都不影响历史记录
CREATE TABLE `exchange_order` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `product_id` int(11) NOT NULL,
  `product_name` varchar(100) NOT NULL,
  `product_image` varchar(500) DEFAULT NULL,
  `score` int(11) NOT NULL COMMENT '这次兑换花掉的积分',
  `contact` varchar(500) DEFAULT NULL COMMENT '用户填的联系方式/收货信息',
  `status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '0 待处理，1 已处理，2 已取消（积分已退还）',
  `remark` varchar(500) DEFAULT NULL COMMENT '管理员备注',
  `in_time` datetime NOT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_exchange_order_user` (`user_id`),
  KEY `idx_exchange_order_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 后台权限（带上 INSERT ... WHERE NOT EXISTS，重复执行不会重复插入），授予超级管理员
INSERT INTO `permission` (`name`, `value`, `pid`)
SELECT '积分兑换', 'exchange', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `permission` x WHERE x.`value` = 'exchange');

INSERT INTO `permission` (`name`, `value`, `pid`)
SELECT '兑换商品列表', 'exchange_product:list', p.`id` FROM `permission` p
WHERE p.`value` = 'exchange' AND p.`pid` = 0
  AND NOT EXISTS (SELECT 1 FROM `permission` x WHERE x.`value` = 'exchange_product:list');
INSERT INTO `permission` (`name`, `value`, `pid`)
SELECT '兑换商品编辑', 'exchange_product:edit', p.`id` FROM `permission` p
WHERE p.`value` = 'exchange' AND p.`pid` = 0
  AND NOT EXISTS (SELECT 1 FROM `permission` x WHERE x.`value` = 'exchange_product:edit');
INSERT INTO `permission` (`name`, `value`, `pid`)
SELECT '兑换商品删除', 'exchange_product:delete', p.`id` FROM `permission` p
WHERE p.`value` = 'exchange' AND p.`pid` = 0
  AND NOT EXISTS (SELECT 1 FROM `permission` x WHERE x.`value` = 'exchange_product:delete');
INSERT INTO `permission` (`name`, `value`, `pid`)
SELECT '兑换记录列表', 'exchange_order:list', p.`id` FROM `permission` p
WHERE p.`value` = 'exchange' AND p.`pid` = 0
  AND NOT EXISTS (SELECT 1 FROM `permission` x WHERE x.`value` = 'exchange_order:list');
INSERT INTO `permission` (`name`, `value`, `pid`)
SELECT '兑换记录处理', 'exchange_order:edit', p.`id` FROM `permission` p
WHERE p.`value` = 'exchange' AND p.`pid` = 0
  AND NOT EXISTS (SELECT 1 FROM `permission` x WHERE x.`value` = 'exchange_order:edit');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 1, p.`id` FROM `permission` p
WHERE p.`value` IN ('exchange', 'exchange_product:list', 'exchange_product:edit', 'exchange_product:delete',
                    'exchange_order:list', 'exchange_order:edit')
  AND NOT EXISTS (SELECT 1 FROM `role_permission` rp WHERE rp.`role_id` = 1 AND rp.`permission_id` = p.`id`);
