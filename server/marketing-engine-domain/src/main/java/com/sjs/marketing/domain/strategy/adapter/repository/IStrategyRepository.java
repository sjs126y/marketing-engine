package com.sjs.marketing.domain.strategy.adapter.repository;

import com.sjs.marketing.domain.strategy.model.entity.RaffleAwardEntity;
import com.sjs.marketing.domain.strategy.model.entity.StrategyAwardEntity;
import com.sjs.marketing.domain.strategy.model.entity.StrategyEntity;
import com.sjs.marketing.domain.strategy.model.entity.StrategyRuleEntity;
import com.sjs.marketing.domain.strategy.model.valobj.RuleTreeVO;
import com.sjs.marketing.domain.strategy.model.valobj.RuleWeightVO;
import com.sjs.marketing.domain.strategy.model.valobj.StrategyAwardRuleModelVO;
import com.sjs.marketing.domain.strategy.model.valobj.StrategyAwardStockKeyVO;

import java.util.Date;
import java.util.List;
import java.util.Map;

public interface IStrategyRepository {

    List<StrategyAwardEntity> queryStrategyAwardList(Long strategyId);

    void storeStrategyAwardSearchRateTables(String key, Integer rateRange, Map<Integer, Integer> strategyAwardSearchRateMap);

    int getRateRange(Long strategyId);

    int getRateRange(String key);

    Integer getStrategyAwardAssemble(String key, int randomNumber);

    StrategyEntity queryStrategyEntityByStrategyId(Long strategyId);

    StrategyRuleEntity queryStrategyRuleEntity(Long strategyId, String ruleModel);

    String queryStrategyRuleValue(Long strategyId, String ruleModel);

    String queryStrategyRuleValue(Long strategyId, Integer awardId, String ruleModel);

    StrategyAwardRuleModelVO queryStrategyAwardRuleModelVO(Long strategyId, Integer awardId);

    RuleTreeVO queryRuleTreeVOByTreeId(String treeId);

    void cacheStrategyAwardCount(String cacheKey, Integer awardCount);

    Integer subtractionAwardStock(String cacheKey);

    Integer subtractionAwardStock(String cacheKey, Date date);

    /**
     * 写入奖品库存消费队列
     *
     * @param strategyAwardStockKeyVO 对象值对象
     */
    void awardStockConsumeSendQueue(StrategyAwardStockKeyVO strategyAwardStockKeyVO);

    /**
     * 获取奖品库存消费队列
     */
    StrategyAwardStockKeyVO takeQueueValue() throws InterruptedException;

    /**
     * 更新奖品库存消耗
     *
     * @param strategyId    策略ID
     * @param awardId       奖品ID
     * @param consumedAfter 扣减后的累计已扣数（绝对量回写，幂等）
     */
    void updateStrategyAwardStock(Long strategyId, Integer awardId, Integer consumedAfter);

    StrategyAwardEntity queryStrategyAwardEntity(Long strategyId, Integer awardId);

    Long queryStrategyIdByActivityId(Long activityId);

    Integer queryTodayUserRaffleCount(String userId, Long strategyId);

    Map<String, Integer> queryAwardRuleLockCount(String[] treeIds);

    Integer queryActivityAccountTotalUseCount(String userId, Long strategyId);

    List<RuleWeightVO> queryAwardRuleWeight(Long strategyId);

    /**
     * 配置变更后失效相关缓存（策略实体、奖品、规则值、权重、规则树 VO）。
     *
     * <p>规则树 VO 只写不失效，改库后不清缓存会导致新配置永远不生效。</p>
     *
     * @param strategyId 策略 ID
     */
    void evictStrategyConfigCache(Long strategyId);
}
