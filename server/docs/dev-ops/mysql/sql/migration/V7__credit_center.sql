-- V7：权益中心资产底座（账户 / 批次 / 流水）
-- 在两个分片库各执行一次：marketing_engine_01、marketing_engine_02
-- 分片规则：按 user_id 分库（与 raffle_activity_account / outbox 同策略）
--
-- 设计要点：
--   1. 三张表同分片键 → 扣账户 + 扣批次 + 写流水落在同一库，一个本地事务可覆盖
--   2. credit_batch.event_id 唯一 → 发放幂等（同一笔业务只入账一次）
--   3. credit_ledger.biz_no  唯一 → 记账幂等
--   4. 有效期统一 30 天（配置项 credit.default-ttl-days），到期由扫描任务回收
--   5. 先分库不分表：当前量级未到，后续流水表可按时间归档或升级为分库分表

CREATE TABLE IF NOT EXISTS `credit_account`
(
    `id`             bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
    `user_id`        varchar(32)         NOT NULL COMMENT '用户ID（分片键）',
    `balance`        int(11)             NOT NULL DEFAULT 0 COMMENT '可用余额（积分/次数共用，按 scene 区分语义）',
    `frozen`         int(11)             NOT NULL DEFAULT 0 COMMENT '冻结数量（发放中/退款中）',
    `total_granted`  int(11)             NOT NULL DEFAULT 0 COMMENT '累计发放',
    `total_consumed` int(11)             NOT NULL DEFAULT 0 COMMENT '累计消耗',
    `total_expired`  int(11)             NOT NULL DEFAULT 0 COMMENT '累计过期回收',
    `create_time`    datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_user_id` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='积分/次数账户';

CREATE TABLE IF NOT EXISTS `credit_batch`
(
    `id`         bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
    `user_id`    varchar(32)         NOT NULL COMMENT '用户ID（分片键）',
    `event_id`   varchar(96)         NOT NULL COMMENT '幂等键：同一笔业务只入账一次',
    `scene`      varchar(16)         NOT NULL COMMENT '来源场景：sign/task/lottery/invite/pay/migrate',
    `amount`     int(11)             NOT NULL COMMENT '发放数量',
    `remaining`  int(11)             NOT NULL COMMENT '剩余可用',
    `granted_at` datetime            NOT NULL COMMENT '发放时间',
    `expires_at` datetime            NOT NULL COMMENT '过期时间（默认 30 天）',
    `state`      varchar(16)         NOT NULL DEFAULT 'active' COMMENT 'active/exhausted/expired/reversed',
    `create_time` datetime           NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime           NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_event_id` (`event_id`),
    KEY `idx_user_state_expire` (`user_id`, `state`, `expires_at`),
    KEY `idx_state_expire` (`state`, `expires_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='权益批次（每笔发放一批，自带有效期）';

CREATE TABLE IF NOT EXISTS `credit_ledger`
(
    `id`            bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
    `user_id`       varchar(32)         NOT NULL COMMENT '用户ID（分片键）',
    `biz_no`        varchar(128)        NOT NULL COMMENT '业务幂等号：scene:bizNo[:batchId]',
    `batch_id`      bigint(20)          NULL COMMENT '关联批次（发放/过期时有，账户级操作可空）',
    `scene`         varchar(16)         NOT NULL COMMENT '来源场景',
    `type`          varchar(16)         NOT NULL COMMENT 'grant/consume/expire/reverse',
    `delta`         int(11)             NOT NULL COMMENT '变动值（正增负减）',
    `balance_after` int(11)             NOT NULL COMMENT '变动后余额',
    `create_time`   datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_biz_no` (`biz_no`),
    KEY `idx_user_time` (`user_id`, `create_time`),
    KEY `idx_batch` (`batch_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='权益流水（append-only）';
