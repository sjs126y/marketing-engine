-- V3：SKU 库存改为绝对量回写
-- 在配置库 marketing_engine 执行（raffle_activity_sku 属于配置表，未分片）
--
-- 背景：原先用 Redisson 延迟队列把库存「减 1」异步落库，队列不保证投递，
--       进程崩溃或 Redis 重启会丢消息，且重复消费会多减。
-- 现在改为：
--   1. Redis DECR 得到扣减后的累计已扣数 consumedAfter；
--   2. 业务事务内把 consumedAfter 写进 outbox；
--   3. 消费端按「库存 = 总量 - consumedAfter」回写，并记 applied_consumed；
--   4. WHERE applied_consumed < consumedAfter 保证单调，重复/乱序都幂等。
--
-- applied_consumed 初始为 0；若历史数据已有扣减，可按下式回填：
--   update raffle_activity_sku
--      set applied_consumed = stock_count - stock_count_surplus
--    where applied_consumed = 0;

ALTER TABLE `raffle_activity_sku`
    ADD COLUMN `applied_consumed` int(11) NOT NULL DEFAULT 0
        COMMENT '已写入数据库的累计已扣数（单调递增，绝对量回写用）' AFTER `stock_count_surplus`;
