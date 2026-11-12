package com.sjs.marketing.domain.strategy.service.rule.chain.impl;

import com.sjs.marketing.domain.strategy.adapter.repository.IStrategyRepository;
import com.sjs.marketing.domain.strategy.model.entity.RuleActionEntity;
import com.sjs.marketing.domain.strategy.model.valobj.RuleLogicCheckTypeVO;
import com.sjs.marketing.domain.strategy.service.armory.IStrategyDispatch;
import com.sjs.marketing.domain.strategy.service.rule.chain.AbstractLogicChain;
import com.sjs.marketing.domain.strategy.service.rule.chain.RuleWeightSelector;
import com.sjs.marketing.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import com.sjs.marketing.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component("rule_weight")
public class RuleWeightLogicChain extends AbstractLogicChain {

    @Resource
    private IStrategyRepository repository;

    @Resource
    private IStrategyDispatch strategyDispatch;


    @Override
    public DefaultChainFactory.StrategyAwardVO logic(String userId, Long strategyId) {
        log.info("规则过滤-权重范围 userId:{} strategyId:{} ruleModel:{}", userId, strategyId, ruleModel());


        String ruleValue = repository.queryStrategyRuleValue(strategyId, this.ruleModel());

        //获取权重
        Map<Long, String> analyticalValueGroup = getAnalyticalValueGroup(ruleValue);
        if(analyticalValueGroup == null || analyticalValueGroup.isEmpty()){
            return next().logic(userId, strategyId);
        }

        // 查询用户累计消耗，选出命中的档位（语义见 RuleWeightSelector）
        Integer userScore = repository.queryActivityAccountTotalUseCount(userId, strategyId);
        String tierToken = RuleWeightSelector.select(analyticalValueGroup, userScore == null ? 0 : userScore);

        if (tierToken != null) {
            Integer awardId = strategyDispatch.getRandomAwardId(strategyId, tierToken);
            log.info("权重抽奖-权重范围 userId:{} strategyId:{} ruleModel:{} tier:{} awardId:{}",
                    userId, strategyId, ruleModel(), tierToken, awardId);
            return DefaultChainFactory.StrategyAwardVO.builder()
                    .awardId(awardId)
                    .logicModel(ruleModel())
                    .build();
        }

        //权重范围未匹配，进行下一步
        log.info("抽奖责任链-权重放行 userId: {} strategyId: {} ruleModel: {}", userId, strategyId, ruleModel());
        return next().logic(userId, strategyId);
    }

    private Map<Long, String> getAnalyticalValueGroup(String ruleValue) {
        String[] ruleValueGroups = ruleValue.split(Constants.SPACE);

        Map<Long, String> ruleValueMap = new HashMap<>();

        for (String ruleValueKey : ruleValueGroups) {
            // 检查输入是否为空
            if (ruleValueKey == null || ruleValueKey.isEmpty()) {
                return ruleValueMap;
            }
            // 分割字符串以获取键和值
            String[] parts = ruleValueKey.split(Constants.COLON);
            if (parts.length != 2) {
                throw new IllegalArgumentException("rule_weight rule_rule invalid input format" + ruleValueKey);
            }
            ruleValueMap.put(Long.parseLong(parts[0]), ruleValueKey);

        }

        return ruleValueMap;
    }

    @Override
    protected String ruleModel() {
        return DefaultChainFactory.LogicModel.RULE_WEIGHT.getCode();
    }
}
