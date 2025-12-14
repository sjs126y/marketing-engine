package com.sjs.domain.strategy.service.armory;

public interface IStrategyArmory {

    boolean assembleLotteryStrategy(Long strategyId);

    boolean assembleLotteryStrategyByActivityId(Long activityId);
}
