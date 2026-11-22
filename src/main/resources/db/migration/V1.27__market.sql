-- 用户集市：用户自己上架商品、自己定价，别的用户用积分购买，积分直接转给卖家

CREATE TABLE `market_item` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `seller_id` int(11) NOT NULL COMMENT '卖家',
  `name` varchar(100) NOT NULL,
  `image` varchar(500) DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL,
  `score` int(11) NOT NULL COMMENT '卖家定的价格（积分）',
  `stock` int(11) NOT NULL DEFAULT -1 COMMENT '库存，-1 不限量',
  `sold` int(11) NOT NULL DEFAULT 0 COMMENT '已卖出份数',
  `status` int(11) NOT NULL DEFAULT 0 COMMENT '0 上架中，1 卖家下架，2 管理员下架',
  `reward_text` text COMMENT '买家付款后才能看到的文本',
  `reward_type` varchar(10) DEFAULT NULL COMMENT '付款后可见的文件类型 image / video',
  `reward_url` varchar(500) DEFAULT NULL,
  `in_time` datetime NOT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_market_item_seller` (`seller_id`),
  KEY `idx_market_item_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 订单里保存商品名称、价格和内容的快照：卖家以后改价、改内容、下架都不影响已经买到的人
CREATE TABLE `market_order` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `item_id` int(11) NOT NULL,
  `buyer_id` int(11) NOT NULL,
  `seller_id` int(11) NOT NULL,
  `item_name` varchar(100) NOT NULL,
  `item_image` varchar(500) DEFAULT NULL,
  `score` int(11) NOT NULL COMMENT '成交价（积分），买家付出的，卖家全额收到',
  `reward_text` text,
  `reward_type` varchar(10) DEFAULT NULL,
  `reward_url` varchar(500) DEFAULT NULL,
  `in_time` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_market_order_buyer` (`buyer_id`),
  KEY `idx_market_order_seller` (`seller_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

INSERT INTO `permission` (`name`, `value`, `pid`)
SELECT '用户集市', 'market', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `permission` x WHERE x.`value` = 'market');

INSERT INTO `permission` (`name`, `value`, `pid`)
SELECT '集市商品列表', 'market_item:list', p.`id` FROM `permission` p
WHERE p.`value` = 'market' AND p.`pid` = 0
  AND NOT EXISTS (SELECT 1 FROM `permission` x WHERE x.`value` = 'market_item:list');
INSERT INTO `permission` (`name`, `value`, `pid`)
SELECT '集市商品下架', 'market_item:edit', p.`id` FROM `permission` p
WHERE p.`value` = 'market' AND p.`pid` = 0
  AND NOT EXISTS (SELECT 1 FROM `permission` x WHERE x.`value` = 'market_item:edit');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 1, p.`id` FROM `permission` p
WHERE p.`value` IN ('market', 'market_item:list', 'market_item:edit')
  AND NOT EXISTS (SELECT 1 FROM `role_permission` rp WHERE rp.`role_id` = 1 AND rp.`permission_id` = p.`id`);
