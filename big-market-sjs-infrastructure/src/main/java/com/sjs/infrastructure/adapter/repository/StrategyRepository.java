package com.sjs.infrastructure.adapter.repository;

import com.sjs.domain.strategy.adapter.repository.IStrategyRepository;
import com.sjs.domain.strategy.model.entity.StrategyAwardEntity;
import com.sjs.infrastructure.dao.IStrategyAwardDao;
import com.sjs.infrastructure.dao.po.StrategyAward;
import com.sjs.infrastructure.redis.IRedisService;
import com.sjs.types.common.Constants;
import org.redisson.api.RMap;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Repository
public class StrategyRepository implements IStrategyRepository {

    @Resource
    private IRedisService redisService;
    @Resource
    private IStrategyAwardDao strategyAwardDao;

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
    public void storeStrategyAwardSearchRateTables(Long strategyId, Integer rateRange, Map<Integer, Integer> strategyAwardSearchRateMap) {
        String rateRangeKey = Constants.RedisKey.STRATEGY_RATE_RANGE_KEY + strategyId;
        String rateTableKey = Constants.RedisKey.STRATEGY_RATE_TABLE_KEY + strategyId;

        //1.先存放奖品概率查找表总数
        redisService.setValue(rateRangeKey, rateRange);

        //2.存放奖品概率查找表
        RMap<Integer, Integer> map = redisService.getMap(rateTableKey);
        map.putAll(strategyAwardSearchRateMap);
    }

    @Override
    public int getRateRange(Long strategyId) {
        String rateRangeKey = Constants.RedisKey.STRATEGY_RATE_RANGE_KEY + strategyId;
        return redisService.getValue(rateRangeKey);
    }

    @Override
    public Integer getStrategyAwardAssemble(Long strategyId, int randomNumber) {
        String rateTableKey = Constants.RedisKey.STRATEGY_RATE_TABLE_KEY + strategyId;
        RMap<Integer, Integer> map = redisService.getMap(rateTableKey);
        return map.get(randomNumber);
    }
}
