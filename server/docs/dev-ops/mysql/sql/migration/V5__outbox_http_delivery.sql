-- V5：outbox 支持 HTTP 投递
-- 在两个分片库各执行一次：marketing_engine_01、marketing_engine_02
--
-- 背景：原先 outbox 只负责"投 MQ"。现在中台还需要把发奖结果
--       **主动回调给上游业务系统**（HTTP POST），于是同一条 outbox 机制
--       要能承载两种投递方式，重试/退避/死信这些能力直接复用。
--
-- delivery_type：MQ（默认）| HTTP
-- target       ：HTTP 时为完整的回调地址；MQ 时留空，用 topic 即可

ALTER TABLE `outbox`
    ADD COLUMN `delivery_type` varchar(16) NOT NULL DEFAULT 'MQ'
        COMMENT '投递方式：MQ / HTTP' AFTER `payload`,
    ADD COLUMN `target` varchar(512) NULL
        COMMENT 'HTTP 投递目标地址（MQ 投递时为空）' AFTER `delivery_type`;
