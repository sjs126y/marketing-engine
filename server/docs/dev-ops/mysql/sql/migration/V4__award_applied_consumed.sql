-- V4：奖品库存改为绝对量回写
-- 在配置库 marketing_engine 执行（strategy_award 属于配置表，未分片）
--
-- 背景：原先按「award_count_surplus - 1」增量回写，队列堆积后重放会把库存扣成负数，
--       而且重复消费没有幂等保护。改成与 SKU 库存一致的绝对量写法：
--         库存 = 总量 - 已扣数，且 WHERE applied_consumed < consumedAfter 保证单调。
--
-- 回填历史值（可选，让基线自洽）：
--   update strategy_award
--      set applied_consumed = award_count - award_count_surplus
--    where applied_consumed = 0;

ALTER TABLE `strategy_award`
    ADD COLUMN `applied_consumed` int(8) NOT NULL DEFAULT 0
        COMMENT '已写入数据库的累计已扣数（单调递增，绝对量回写用）' AFTER `award_count_surplus`;
