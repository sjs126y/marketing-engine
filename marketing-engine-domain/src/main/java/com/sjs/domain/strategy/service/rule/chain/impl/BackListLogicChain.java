package com.sjs.domain.strategy.service.rule.chain.impl;

import com.sjs.domain.strategy.adapter.repository.IStrategyRepository;
import com.sjs.domain.strategy.service.rule.chain.AbstractLogicChain;
import com.sjs.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import com.sjs.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component("rule_blacklist")
public class BackListLogicChain extends AbstractLogicChain {

    @Resource
    private IStrategyRepository repository;

    @Override
    public DefaultChainFactory.StrategyAwardVO logic(String userId, Long strategyId) {
        log.info("责任链过滤-黑名单 userId:{} strategyId:{} ruleModel:{}", userId, strategyId, ruleModel());


        //查询黑名单规则
        String ruleValue = repository.queryStrategyRuleValue(strategyId, this.ruleModel());
        String[] splitRuleValue = ruleValue.split(Constants.COLON);
        Integer awardId = Integer.parseInt(splitRuleValue[0]);

        //开始过滤是否为黑名单
        String[] userBackIds = splitRuleValue[1].split(Constants.SPLIT);
        for (String userBackId : userBackIds) {
            if(userId.equals(userBackId)){
                log.info("抽奖责任链-黑名单接管 userId: {} strategyId: {} ruleModel: {} awardId: {}", userId, strategyId, ruleModel(), awardId);
                return DefaultChainFactory.StrategyAwardVO.builder()
                        .awardId(awardId)
                        .logicModel(ruleModel())
                        .build();
            }
        }

        //不是黑名单，进行下一步
        log.info("抽奖责任链-黑名单放行 userId: {} strategyId: {} ruleModel: {}", userId, strategyId, ruleModel());
        return next().logic(userId, strategyId);
    }

    @Override
    protected String ruleModel() {
        return DefaultChainFactory.LogicModel.RULE_BLACKLIST.getCode();
    }
}
