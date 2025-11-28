package com.sjs.domain.strategy.service.armory;

public interface IStrategyArmoryService {

    boolean assembleLotteryStrategy(Long strategyId);

    Integer getRandomAwardId(Long strategyId);

}
