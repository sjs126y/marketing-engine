-- V8：权益对账差异表（P1 ② 对账）
-- 在两个分片库各执行一次：marketing_engine_01、marketing_engine_02
--
-- 设计要点：
--   1. 只暴露不修正：差异落表 + 告警，修不修由人决定
--   2. user_id 是分片键：差异和账户同库，便于按用户排查
--   3. run_id 串起一次全量对账，事后可以按批次复盘

CREATE TABLE IF NOT EXISTS `credit_reconcile_diff`
(
    `id`              bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
    `run_id`          varchar(32)         NOT NULL COMMENT '对账批次号（一次全量对账一个）',
    `user_id`         varchar(32)         NOT NULL COMMENT '用户ID（分片键）',
    `account_balance` int(11)             NOT NULL COMMENT '账户余额',
    `batch_remaining` int(11)             NOT NULL COMMENT '未核销批次剩余之和',
    `ledger_sum`      int(11)             NOT NULL COMMENT '流水累计',
    `diff`            int(11)             NOT NULL COMMENT '账户 − 批次',
    `reason`          varchar(255)        NOT NULL COMMENT '差异原因（人可读）',
    `state`           varchar(16)         NOT NULL DEFAULT 'open' COMMENT 'open / ignored',
    `create_time`     datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_run_user` (`run_id`, `user_id`),
    KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='权益对账差异（只暴露不修正）';
