package com.sjs.infrastructure.adapter.repository;

import com.sjs.domain.strategy.adapter.repository.IStrategyRepository;
import com.sjs.domain.strategy.model.entity.StrategyAwardEntity;
import com.sjs.domain.strategy.model.entity.StrategyEntity;
import com.sjs.domain.strategy.model.entity.StrategyRuleEntity;
import com.sjs.infrastructure.dao.IStrategyAwardDao;
import com.sjs.infrastructure.dao.IStrategyDao;
import com.sjs.infrastructure.dao.IStrategyRuleDao;
import com.sjs.infrastructure.dao.po.Strategy;
import com.sjs.infrastructure.dao.po.StrategyAward;
import com.sjs.infrastructure.dao.po.StrategyRule;
import com.sjs.infrastructure.redis.IRedisService;
import com.sjs.types.common.Constants;
import org.redisson.api.RMap;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class StrategyRepository implements IStrategyRepository {

    @Resource
    private IRedisService redisService;
    @Resource
    private IStrategyAwardDao strategyAwardDao;
    @Resource
    private IStrategyDao strategyDao;
    @Resource
    private IStrategyRuleDao strategyRuleDao;

    @Override
    public List<StrategyAwardEntity> queryStrategyAwardList(Long strategyId) {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_KEY + strategyId;

        List<StrategyAwardEntity> strategyAwardEntities = redisService.getValue(cacheKey);
        if(strategyAwardEntities != null && !strategyAwardEntities.isEmpty()){
            return strategyAwardEntities;
        }

        List<StrategyAward> strategyAwards = strategyAwardDao.queryStrategyAwardListByStrategyId(strategyId);
        if(strategyAwards == null ) return null;

        strategyAwardEntities = new ArrayList<>(strategyAwards.size());
        for (StrategyAward strategyAward : strategyAwards) {
            StrategyAwardEntity strategyAwardEntity = StrategyAwardEntity.builder()
                    .strategyId(strategyAward.getStrategyId())
                    .awardId(strategyAward.getAwardId())
                    .awardCount(strategyAward.getAwardCount())
                    .awardCountSurplus(strategyAward.getAwardCountSurplus())
                    .awardRate(strategyAward.getAwardRate())
                    .build();
            strategyAwardEntities.add(strategyAwardEntity);
        }

        return strategyAwardEntities;
    }

    @Override
    public void storeStrategyAwardSearchRateTables(String key, Integer rateRange, Map<Integer, Integer> strategyAwardSearchRateMap) {
        String rateRangeKey = Constants.RedisKey.STRATEGY_RATE_RANGE_KEY + key;
        String rateTableKey = Constants.RedisKey.STRATEGY_RATE_TABLE_KEY + key;

        //1.先存放奖品概率查找表总数
        redisService.setValue(rateRangeKey, rateRange);

        //2.存放奖品概率查找表
        RMap<Integer, Integer> map = redisService.getMap(rateTableKey);
        map.putAll(strategyAwardSearchRateMap);
    }

    @Override
    public int getRateRange(Long strategyId) {
        return getRateRange(String.valueOf(strategyId));
    }
    @Override
    public int getRateRange(String key) {
        String rateRangeKey = Constants.RedisKey.STRATEGY_RATE_RANGE_KEY + key;
        return redisService.getValue(rateRangeKey);
    }

    @Override
    public Integer getStrategyAwardAssemble(String key, int randomNumber) {
        String rateTableKey = Constants.RedisKey.STRATEGY_RATE_TABLE_KEY + key;
        RMap<Integer, Integer> map = redisService.getMap(rateTableKey);
        return map.get(randomNumber);
    }

    @Override
    public StrategyEntity queryStrategyEntityByStrategyId(Long strategyId) {
        // 优先从缓存获取
        String cacheKey = Constants.RedisKey.STRATEGY_KEY + strategyId;
        StrategyEntity strategyEntity = redisService.getValue(cacheKey);
        if (null != strategyEntity) return strategyEntity;
        Strategy strategy = strategyDao.queryStrategyByStrategyId(strategyId);
        strategyEntity = StrategyEntity.builder()
                .strategyId(strategy.getStrategyId())
                .strategyDesc(strategy.getStrategyDesc())
                .ruleModels(strategy.getRuleModels())
                .build();
        redisService.setValue(cacheKey, strategyEntity);
        return strategyEntity;
    }

    @Override
    public StrategyRuleEntity queryStrategyRuleEntity(Long strategyId, String ruleModel) {
        StrategyRule strategyRuleReq = new StrategyRule();
        strategyRuleReq.setStrategyId(strategyId);
        strategyRuleReq.setRuleModel(ruleModel);
        StrategyRule strategyRuleRes = strategyRuleDao.queryStrategyRule(strategyRuleReq);
        return StrategyRuleEntity.builder()
                .strategyId(strategyRuleRes.getStrategyId())
                .awardId(strategyRuleRes.getAwardId())
                .ruleType(strategyRuleRes.getRuleType())
                .ruleModel(strategyRuleRes.getRuleModel())
                .ruleValue(strategyRuleRes.getRuleValue())
                .ruleDesc(strategyRuleRes.getRuleDesc())
                .build();
    }

    @Override
    public String queryStrategyRuleValue(Long strategyId, Integer awardId, String ruleModel) {
        StrategyRule strategyRuleReq = new StrategyRule();
        strategyRuleReq.setStrategyId(strategyId);
        strategyRuleReq.setAwardId(awardId);
        strategyRuleReq.setRuleModel(ruleModel);

        return strategyRuleDao.queryStrategyRuleValue(strategyRuleReq);
    }
}
