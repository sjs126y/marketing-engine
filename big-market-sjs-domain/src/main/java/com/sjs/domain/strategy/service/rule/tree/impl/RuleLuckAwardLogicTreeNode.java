package com.sjs.domain.strategy.service.rule.tree.impl;

import com.sjs.domain.strategy.model.valobj.RuleLogicCheckTypeVO;
import com.sjs.domain.strategy.service.rule.tree.ILogicTreeNode;
import com.sjs.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import com.sjs.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("rule_luck_award")
public class RuleLuckAwardLogicTreeNode implements ILogicTreeNode {
    @Override
    public DefaultTreeFactory.TreeActionEntity logic(String userId, Long strategyId, Integer awardId, String ruleValue) {
        log.info("规则过滤-兜底奖品 userId:{} strategyId:{} awardId:{} ruleValue:{}", userId, strategyId, awardId, ruleValue);

        String[] ruleValues = ruleValue.split(Constants.COLON);
        if(ruleValues.length != 2){
            log.error("规则过滤-兜底奖品，兜底奖品未配置告警 userId:{} strategyId:{} awardId:{}", userId, strategyId, awardId);
            throw new RuntimeException("规则过滤-兜底奖品 ruleValue: " + ruleValue + " 配置不正确");
        }

        //兜底奖励配置
        String luckAwardId = ruleValues[0];
        String awardRuleValue = ruleValues.length > 1 ? ruleValues[1] : "1";

        //返回数据
        return DefaultTreeFactory.TreeActionEntity.builder()
                .ruleLogicCheckType(RuleLogicCheckTypeVO.ALLOW)
                .strategyAwardVO(DefaultTreeFactory.StrategyAwardVO.builder()
                        .awardId(Integer.parseInt(luckAwardId))
                        .awardRuleValue(awardRuleValue)
                        .build())
                .build();
    }
}
