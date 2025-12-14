package com.sjs.domain.strategy.service;

import com.sjs.domain.strategy.model.entity.StrategyAwardEntity;

import java.util.List;

public interface IRaffleAward {

    /**
     * 查询策略奖品列表
     * @param strategyId
     * @return
     */
    List<StrategyAwardEntity> queryStrategyAwardList(Long strategyId);

    /**
     * 查询策略奖品列表
     * @param activityId
     * @return
     */
    List<StrategyAwardEntity> queryStrategyAwardListByActivityId(Long activityId);

}
