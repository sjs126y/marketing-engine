package com.sjs.domain.strategy.service.rule.filter;


import com.sjs.domain.strategy.model.entity.RuleActionEntity;
import com.sjs.domain.strategy.model.entity.RuleMatterEntity;

/**
 * @author marketing-engine contributors
 * @description 抽奖规则过滤接口
 * @create 2024-01-06 09:55
 */
public interface ILogicFilter<T extends RuleActionEntity.RaffleEntity> {

    RuleActionEntity<T> filter(RuleMatterEntity ruleMatterEntity);

}
