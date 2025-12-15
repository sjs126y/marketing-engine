package com.sjs.domain.strategy.service;

import com.sjs.domain.strategy.model.valobj.RuleWeightVO;

import java.util.List;
import java.util.Map;

public interface IRaffleRule {

    Map<String, Integer> queryAwardRuleLockCount(String... treeIds);

    /**
     * 查询奖品权重配置
     *
     * @param strategyId 策略ID
     * @return 权重规则
     */
    List<RuleWeightVO> queryAwardRuleWeight(Long strategyId);

    List<RuleWeightVO> queryAwardRuleWeightByActivityId(Long activityId);

}
