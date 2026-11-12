-- 兑换之后给用户看的内容：后台给商品提前设置好（文本、图片或视频），兑换成功后用户就能看到。
-- 兑换记录里同样保存一份快照：以后商品改了内容、改了文件、删除了，已经兑换的用户看到的仍然是兑换时的内容。
-- 不再收集联系方式：exchange_order.contact 列保留（老记录里可能有内容），但不再读写、也不再展示。

ALTER TABLE `exchange_product` ADD COLUMN `reward_text` text DEFAULT NULL COMMENT '兑换后给用户看的文本';
ALTER TABLE `exchange_product` ADD COLUMN `reward_type` varchar(10) DEFAULT NULL COMMENT '兑换后给用户看的文件类型：image / video';
ALTER TABLE `exchange_product` ADD COLUMN `reward_url` varchar(500) DEFAULT NULL COMMENT '兑换后给用户看的文件地址';

ALTER TABLE `exchange_order` ADD COLUMN `reward_text` text DEFAULT NULL COMMENT '兑换时的文本快照';
ALTER TABLE `exchange_order` ADD COLUMN `reward_type` varchar(10) DEFAULT NULL COMMENT '兑换时的文件类型快照';
ALTER TABLE `exchange_order` ADD COLUMN `reward_url` varchar(500) DEFAULT NULL COMMENT '兑换时的文件地址快照';

-- 兑换后自动发放：新记录直接是“已兑换”（1）。老的“待处理”（0）记录保留，不再有“标记已处理”这个操作。
