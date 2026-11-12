package com.sjs.marketing.domain.strategy.service.armory;

import java.util.Date;

public interface IStrategyDispatch {
    Integer getRandomAwardId(Long strategyId);

    Integer getRandomAwardId(Long strategyId, String ruleWeightValue);

    /**
     * Redis 原子扣减奖品库存。
     *
     * @return 扣减后的剩余库存；{@code null} 表示库存不足
     */
    Integer subtractionAwardStock(Long strategyId, Integer awardId, Date date);
}
