package com.sjs.marketing.domain.strategy.service.armory;

public interface IStrategyArmory {

    boolean assembleLotteryStrategy(Long strategyId);

    boolean assembleLotteryStrategyByActivityId(Long activityId);
}
