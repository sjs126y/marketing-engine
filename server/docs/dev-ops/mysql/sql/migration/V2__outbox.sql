-- V2：本地消息表（outbox）
-- 在两个分片库各执行一次：marketing_engine_01、marketing_engine_02
-- 分片规则：按 user_id 分库（与 task 表同策略），因此同一用户的消息落在同一库
--
-- 状态机：pending → sending → sent → consumed
--         失败重试 8 次后 → dead（人工介入）
-- 说明：
--   1. message_id 唯一，消费端回执与幂等都以它为准
--   2. next_retry_at 用于指数退避；sent_at 用于发现「已投递但未消费」的消息
--   3. 不建外键，消息表与业务表同库不同表，靠业务事务保证一致性

CREATE TABLE IF NOT EXISTS `outbox`
(
    `id`            bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
    `user_id`       varchar(32)         NOT NULL COMMENT '用户ID（分片键）',
    `topic`         varchar(64)         NOT NULL COMMENT '消息主题',
    `message_id`    varchar(64)         NOT NULL COMMENT '消息唯一ID，消费端回执用',
    `payload`       varchar(2048)       NOT NULL COMMENT '消息体（JSON）',
    `state`         varchar(16)         NOT NULL DEFAULT 'pending' COMMENT 'pending/sending/sent/consumed/dead',
    `retry_count`   int(11)             NOT NULL DEFAULT 0 COMMENT '重试次数',
    `next_retry_at` datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次可投递时间',
    `sent_at`       datetime            NULL COMMENT '投递成功时间',
    `consumed_at`   datetime            NULL COMMENT '消费回执时间',
    `created_at`    datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_message_id` (`message_id`),
    KEY `idx_state_retry` (`state`, `next_retry_at`),
    KEY `idx_state_sent` (`state`, `sent_at`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='本地消息表（outbox）';
