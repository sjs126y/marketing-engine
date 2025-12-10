package com.sjs.domain.strategy.service;

import com.sjs.domain.strategy.model.entity.StrategyAwardEntity;

import java.util.List;

public interface IRaffleAward {

    List<StrategyAwardEntity> queryStrategyAwardList(Long strategyId);

}
