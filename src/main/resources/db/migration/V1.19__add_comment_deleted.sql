ALTER TABLE `comment` ADD COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除';
