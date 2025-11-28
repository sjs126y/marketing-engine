package com.sjs.domain.strategy.adapter.repository;

import com.sjs.domain.strategy.model.entity.StrategyAwardEntity;

import java.util.List;
import java.util.Map;

public interface IStrategyRepository {

    List<StrategyAwardEntity> queryStrategyAwardList(Long strategyId);

    void storeStrategyAwardSearchRateTables(Long strategyId, Integer rateRange, Map<Integer, Integer> strategyAwardSearchRateMap);

    int getRateRange(Long strategyId);

    Integer getStrategyAwardAssemble(Long strategyId, int randomNumber);
}
