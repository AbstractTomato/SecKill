CREATE TABLE IF NOT EXISTS `goods` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'goods id',
    `goods_name` VARCHAR(64) NOT NULL COMMENT 'goods name',
    `goods_title` VARCHAR(128) DEFAULT NULL COMMENT 'goods title',
    `goods_img` VARCHAR(256) DEFAULT NULL COMMENT 'goods image url',
    `goods_detail` TEXT COMMENT 'goods detail',
    `goods_price` DECIMAL(10,2) NOT NULL COMMENT 'market price',
    `goods_stock` INT NOT NULL DEFAULT 0 COMMENT 'total stock',
    INDEX `idx_goods_name` (`goods_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='goods table';

CREATE TABLE IF NOT EXISTS `seckill_goods` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'seckill goods id',
    `goods_id` BIGINT NOT NULL COMMENT 'goods id',
    `seckill_price` DECIMAL(10,2) NOT NULL COMMENT 'seckill price',
    `stock_count` INT NOT NULL COMMENT 'seckill stock',
    `start_date` DATETIME NOT NULL COMMENT 'seckill start time',
    `end_date` DATETIME NOT NULL COMMENT 'seckill end time',
    INDEX `idx_start_end` (`start_date`, `end_date`),
    UNIQUE KEY `uk_goods_id` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='seckill goods table';

CREATE TABLE IF NOT EXISTS `order_info` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'order id',
    `user_id` BIGINT NOT NULL COMMENT 'user id',
    `goods_id` BIGINT NOT NULL COMMENT 'goods id',
    `goods_name` VARCHAR(64) NOT NULL COMMENT 'goods name',
    `goods_count` INT NOT NULL DEFAULT 1 COMMENT 'goods count',
    `goods_price` DECIMAL(10,2) NOT NULL COMMENT 'order price',
    `order_status` INT NOT NULL DEFAULT 0 COMMENT 'order status',
    `create_date` DATETIME NOT NULL COMMENT 'create time',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_goods_id` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='order info table';

CREATE TABLE IF NOT EXISTS `seckill_order` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'seckill order id',
    `user_id` BIGINT NOT NULL COMMENT 'user id',
    `order_id` BIGINT NOT NULL COMMENT 'order id',
    `goods_id` BIGINT NOT NULL COMMENT 'goods id',
    UNIQUE KEY `uk_user_goods` (`user_id`, `goods_id`),
    INDEX `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='seckill order table';

INSERT INTO `goods` (`id`, `goods_name`, `goods_title`, `goods_img`, `goods_detail`, `goods_price`, `goods_stock`)
VALUES
    (1, 'iPhone 17 Pro Max', 'Flagship large-screen Pro Max, flash sale discount 2000', 'https://via.placeholder.com/300x300?text=iPhone+17+Pro+Max', '6.9-inch XDR display, A19 Pro chip, 48MP triple camera system, titanium design, starts from 256GB.', 9999.00, 80),
    (2, 'iPhone 17 Pro', 'Compact flagship Pro, flash sale discount 1700', 'https://via.placeholder.com/300x300?text=iPhone+17+Pro', '6.3-inch XDR display, A19 Pro chip, 48MP triple camera system, titanium design, starts from 128GB.', 8999.00, 100),
    (3, 'iPhone 17 Air', 'Ultra-thin Air design, flash sale discount 1200', 'https://via.placeholder.com/300x300?text=iPhone+17+Air', '6.6-inch OLED full-screen display, A19 chip, 48MP dual camera system, 6.1mm ultra-thin body, starts from 128GB.', 6999.00, 120),
    (4, 'iPhone 17', 'Standard iPhone, flash sale discount 1000', 'https://via.placeholder.com/300x300?text=iPhone+17', '6.1-inch OLED full-screen display, A18 chip, 48MP dual camera system, aluminum design, starts from 128GB.', 5999.00, 100),
    (5, 'Xiaomi 17 Max', 'Large-battery imaging flagship, flash sale discount 1600', 'https://via.placeholder.com/300x300?text=Xiaomi+17+Max', '6.9-inch 1.5K straight display, flagship Snapdragon platform, 200MP main camera, 8000mAh battery, 100W wired fast charging, starts from 256GB.', 5299.00, 70)
ON DUPLICATE KEY UPDATE
    `goods_name` = VALUES(`goods_name`),
    `goods_title` = VALUES(`goods_title`),
    `goods_img` = VALUES(`goods_img`),
    `goods_detail` = VALUES(`goods_detail`),
    `goods_price` = VALUES(`goods_price`),
    `goods_stock` = VALUES(`goods_stock`);

INSERT INTO `seckill_goods` (`id`, `goods_id`, `seckill_price`, `stock_count`, `start_date`, `end_date`)
VALUES
    (1, 1, 7999.00, 80, '2026-05-31 20:00:00', '2026-05-31 20:03:00'),
    (2, 2, 7299.00, 100, '2026-05-31 20:00:00', '2026-05-31 20:03:00'),
    (3, 3, 5799.00, 120, '2026-05-31 20:00:00', '2026-05-31 20:03:00'),
    (4, 4, 4999.00, 100, '2026-05-31 20:00:00', '2026-05-31 20:03:00'),
    (5, 5, 3699.00, 70, '2026-05-31 20:00:00', '2026-05-31 20:03:00')
ON DUPLICATE KEY UPDATE
    `seckill_price` = VALUES(`seckill_price`),
    `stock_count` = VALUES(`stock_count`),
    `start_date` = VALUES(`start_date`),
    `end_date` = VALUES(`end_date`);
