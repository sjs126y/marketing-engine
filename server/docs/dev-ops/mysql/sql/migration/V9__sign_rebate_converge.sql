-- V9：签到返利收敛（P1 ③）
-- 在配置库执行：marketing_engine
--
-- 背景：sign 行为在 daily_behavior_rebate 里配了两条返利——
--   rebate_type = 'sku'      → 送抽奖次数（保留：抽奖要靠它）
--   rebate_type = 'integral' → 返 10 积分（删除）
--
-- 主站 /api/sign 已经自己调 /open/v1/credit/grant 发 10 积分，
-- 中台再走 integral 返利回调上游，同一笔签到一起发 20 积分（双发）。
-- 收敛口径：积分的唯一发放出口在主站；中台的签到只负责「判定 + 发抽奖次数」。
--
-- 回滚：
--   INSERT INTO daily_behavior_rebate(behavior_type, rebate_type, rebate_desc, rebate_config)
--   VALUES ('sign', 'integral', '签到返利-积分', '10');

DELETE
FROM daily_behavior_rebate
WHERE behavior_type = 'sign'
  AND rebate_type = 'integral';
