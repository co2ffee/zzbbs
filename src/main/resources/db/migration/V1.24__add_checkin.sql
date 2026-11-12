-- 每日签到：每人每天最多一条（唯一索引兜底，防并发重复签到）
CREATE TABLE `checkin` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `checkin_date` varchar(10) NOT NULL COMMENT '签到日期 yyyy-MM-dd（按站点时区）',
  `score` int(11) NOT NULL COMMENT '这次签到获得的积分',
  `streak` int(11) NOT NULL DEFAULT 1 COMMENT '连续签到天数（含当天）',
  `in_time` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_checkin_user_date` (`user_id`, `checkin_date`),
  KEY `idx_checkin_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 积分配置（pid 26 = 积分配置分组）：签到开关、每天随机获得积分的范围（含两端）
INSERT INTO `system_config` (`key`, `value`, `description`, `pid`, `type`, `option`, `reboot`)
VALUES ('checkin_enabled', '1', '是否开启每日签到', 26, 'radio', null, 0);
INSERT INTO `system_config` (`key`, `value`, `description`, `pid`, `type`, `option`, `reboot`)
VALUES ('checkin_score_min', '1', '每日签到随机获得的积分：最小值（含）', 26, 'number', null, 0);
INSERT INTO `system_config` (`key`, `value`, `description`, `pid`, `type`, `option`, `reboot`)
VALUES ('checkin_score_max', '10', '每日签到随机获得的积分：最大值（含）；设成和最小值一样就是固定积分', 26, 'number', null, 0);
