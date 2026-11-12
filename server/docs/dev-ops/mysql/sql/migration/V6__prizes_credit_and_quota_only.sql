-- V6：策略 100006 的奖品只保留「积分」和「对话次数」
-- 在配置库 marketing_engine 执行
--
-- 做三件事：
--   1. award 表：把 101~108 的发奖渠道收敛成两类
--        user_credit_random → 积分
--        openai_use_count   → 对话次数
--      去掉实物（台灯/椅子/游戏机）与模型解锁（gpt-4 / dall-e）
--   2. strategy_award：改标题、概率（之和归一为 1）、库存
--   3. 修正 tree_lock_3 的错误连线：ALLOW 边应指向 rule_stock，
--      否则该奖品永远不会扣库存、也永远发不出去
--
--
-- 重要：执行时必须显式指定字符集，否则中文会以 latin1 写入而变成乱码：
--   docker exec -i mysql mysql -uroot -p123456 --default-character-set=utf8mb4 marketing_engine < V6__....sql
-- 不指定时 mysql 客户端可能按 latin1 连接，UTF-8 中文会被双重编码（如「积分」→「ç§¯åˆ†」）。
--
-- 执行后必须清掉 Redis 里的策略缓存并重新预热，否则抽奖仍走旧概率表。

-- ---------- 1. 奖品定义 ----------
update award set award_key = 'user_credit_random', award_config = '5',  award_desc = '5 积分'        where award_id = 101;
update award set award_key = 'user_credit_random', award_config = '10', award_desc = '10 积分'       where award_id = 102;
update award set award_key = 'user_credit_random', award_config = '20', award_desc = '20 积分'       where award_id = 103;
update award set award_key = 'openai_use_count',   award_config = '1',  award_desc = '1 次对话额度'   where award_id = 104;
update award set award_key = 'openai_use_count',   award_config = '3',  award_desc = '3 次对话额度'   where award_id = 105;
update award set award_key = 'openai_use_count',   award_config = '5',  award_desc = '5 次对话额度'   where award_id = 106;
update award set award_key = 'openai_use_count',   award_config = '10', award_desc = '10 次对话额度'  where award_id = 107;
update award set award_key = 'user_credit_random', award_config = '1',  award_desc = '1 积分（保底）' where award_id = 108;

-- ---------- 2. 策略奖品：概率之和 = 1.0000 ----------
-- 积分 4 档（40% + 25% + 10% + 0.1% 保底）、对话次数 4 档（15% + 6% + 3% + 0.9%）
update strategy_award
   set award_title = '5 积分', award_rate = 0.4000,
       award_count = 20000, award_count_surplus = 20000, applied_consumed = 0,
       rule_models = 'tree_luck_award', sort = 1
 where strategy_id = 100006 and award_id = 101;

update strategy_award
   set award_title = '10 积分', award_rate = 0.2500,
       award_count = 10000, award_count_surplus = 10000, applied_consumed = 0,
       rule_models = 'tree_luck_award', sort = 2
 where strategy_id = 100006 and award_id = 102;

update strategy_award
   set award_title = '20 积分', award_rate = 0.1000,
       award_count = 5000, award_count_surplus = 5000, applied_consumed = 0,
       rule_models = 'tree_luck_award', sort = 3
 where strategy_id = 100006 and award_id = 103;

update strategy_award
   set award_title = '1 次对话', award_rate = 0.1500,
       award_count = 5000, award_count_surplus = 5000, applied_consumed = 0,
       rule_models = 'tree_luck_award', sort = 4
 where strategy_id = 100006 and award_id = 104;

update strategy_award
   set award_title = '3 次对话', award_rate = 0.0600,
       award_count = 2000, award_count_surplus = 2000, applied_consumed = 0,
       rule_models = 'tree_lock_3', sort = 5
 where strategy_id = 100006 and award_id = 105;

update strategy_award
   set award_title = '5 次对话', award_rate = 0.0300,
       award_count = 1000, award_count_surplus = 1000, applied_consumed = 0,
       rule_models = 'tree_lock_2', sort = 6
 where strategy_id = 100006 and award_id = 106;

update strategy_award
   set award_title = '10 次对话', award_rate = 0.0090,
       award_count = 300, award_count_surplus = 300, applied_consumed = 0,
       rule_models = 'tree_lock_1', sort = 7
 where strategy_id = 100006 and award_id = 107;

update strategy_award
   set award_title = '1 积分（保底）', award_rate = 0.0010,
       award_count = 50000, award_count_surplus = 50000, applied_consumed = 0,
       rule_models = 'tree_luck_award', sort = 8
 where strategy_id = 100006 and award_id = 108;

-- ---------- 3. 权重档位：消耗越高，可抽的奖品越好 ----------
-- 10 档 → 5/10 积分；70 档 → 20 积分与 1 次对话；1000 档 → 3/5/10 次对话
update strategy_rule
   set rule_value = '10:101,102 70:103,104 1000:105,106,107'
 where strategy_id = 100006 and rule_model = 'rule_weight';

-- ---------- 4. 修正 tree_lock_3 的连线 ----------
-- 原配置里 rule_lock 的 ALLOW 边直接指向兜底节点，导致 rule_stock 成了孤岛：
-- 该奖品抽中后既不扣库存，也永远发不出去。
update rule_tree_node_line
   set rule_node_to = 'rule_stock'
 where tree_id = 'tree_lock_3'
   and rule_node_from = 'rule_lock'
   and rule_limit_value = 'ALLOW';
