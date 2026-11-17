-- V11：退款与支付三方对账（P2 收尾）
-- 在两个分片库各执行一次：marketing_engine_01、marketing_engine_02
--
-- 设计要点：
--   1. 退款金额按「该笔订单对应的批次还剩多少」折算，不是按订单状态拍脑袋
--   2. 退款是反向记账：批次置 reversed + 账户扣减 + 写 reverse 流水，幂等键 refund:{orderNo}
--   3. 三方对账 = 支付订单状态 / 订单金额 / 权益流水，差异落表，只暴露不修正

-- ---------- pay_order 增加退款字段（幂等：列已存在就跳过） ----------
SET @has_refunded_cent := (SELECT COUNT(*) FROM information_schema.columns
                           WHERE table_schema = DATABASE() AND table_name = 'pay_order' AND column_name = 'refunded_cent');
SET @sql := IF(@has_refunded_cent = 0,
    'ALTER TABLE pay_order ADD COLUMN refunded_cent int(11) NOT NULL DEFAULT 0 COMMENT ''已退金额（分）''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_refunded_at := (SELECT COUNT(*) FROM information_schema.columns
                         WHERE table_schema = DATABASE() AND table_name = 'pay_order' AND column_name = 'refunded_at');
SET @sql := IF(@has_refunded_at = 0,
    'ALTER TABLE pay_order ADD COLUMN refunded_at datetime NULL COMMENT ''退款时间''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 支付三方对账差异表 ----------
CREATE TABLE IF NOT EXISTS `pay_reconcile_diff`
(
    `id`             bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
    `run_id`         varchar(32)         NOT NULL COMMENT '对账批次号',
    `order_no`       varchar(64)         NOT NULL COMMENT '订单号',
    `user_id`        varchar(32)         NOT NULL COMMENT '用户ID（分片键）',
    `order_state`    varchar(16)         NOT NULL COMMENT '订单当前状态',
    `order_amount`   int(11)             NOT NULL COMMENT '订单应发数量',
    `ledger_amount`  int(11)             NOT NULL DEFAULT 0 COMMENT '权益流水实际入账（发放为正、冲正为负）',
    `refunded_cent`  int(11)             NOT NULL DEFAULT 0 COMMENT '已退金额（分）',
    `reason`         varchar(255)        NOT NULL COMMENT '差异原因（人可读）',
    `state`          varchar(16)         NOT NULL DEFAULT 'open' COMMENT 'open / ignored',
    `create_time`    datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_run` (`run_id`),
    KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='支付三方对账差异（只暴露不修正）';
