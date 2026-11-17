-- V10：支付闭环（P2）
-- 在两个分片库各执行一次：marketing_engine_01、marketing_engine_02
--
-- 设计要点：
--   1. pay_order.order_no 唯一 → 下单幂等（上游业务单号即订单号）
--   2. pay_callback_log.out_trade_no 唯一 → 回调幂等（微信 24h 最多通知 8 次）
--   3. 状态只能往前走：created → paid → granted，另有 created → closed
--   4. 两张表都按 user_id 分库（DB_ONLY_TABLES），保证订单/回调与账户同库

CREATE TABLE IF NOT EXISTS `pay_order`
(
    `id`            bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
    `order_no`      varchar(64)         NOT NULL COMMENT '订单号（上游业务单号，下单幂等键）',
    `user_id`       varchar(32)         NOT NULL COMMENT '用户ID（分片键）',
    `sku`           varchar(32)         NOT NULL COMMENT '商品编码',
    `product_type`  varchar(16)         NOT NULL COMMENT '商品类型：credit / quota',
    `amount`        int(11)             NOT NULL COMMENT '发放数量',
    `amount_cent`   int(11)             NOT NULL COMMENT '应付金额（分）',
    `state`         varchar(16)         NOT NULL DEFAULT 'created' COMMENT 'created/paid/granted/closed/refunded',
    `out_trade_no`  varchar(64)         NULL COMMENT '支付平台单号（回调幂等键）',
    `paid_at`       datetime            NULL COMMENT '支付时间',
    `granted_at`    datetime            NULL COMMENT '发放时间',
    `closed_at`     datetime            NULL COMMENT '关闭时间',
    `create_time`   datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_order_no` (`order_no`),
    KEY `idx_user_state` (`user_id`, `state`, `create_time`),
    KEY `idx_state_time` (`state`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='支付订单';

CREATE TABLE IF NOT EXISTS `pay_callback_log`
(
    `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
    `out_trade_no` varchar(64)         NOT NULL COMMENT '支付平台单号（回调幂等键）',
    `order_no`     varchar(64)         NOT NULL COMMENT '商户订单号',
    `user_id`      varchar(32)         NOT NULL COMMENT '用户ID（分片键）',
    `notify_seq`   int(11)             NULL COMMENT '第几次通知（演示重复通知）',
    `raw`          text                NULL COMMENT '原始报文',
    `result`       varchar(32)         NULL COMMENT '处理结果：RECEIVED/DUPLICATE/AMOUNT_MISMATCH/CLOSED',
    `create_time`  datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_out_trade_no` (`out_trade_no`),
    KEY `idx_order` (`order_no`),
    KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='支付回调日志（幂等 + 留痕）';
