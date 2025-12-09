package com.sjs.domain.strategy.service.raffle;

import com.sjs.domain.strategy.adapter.repository.IStrategyRepository;
import com.sjs.domain.strategy.model.entity.RaffleFactorEntity;
import com.sjs.domain.strategy.model.entity.RuleActionEntity;
import com.sjs.domain.strategy.model.entity.RuleMatterEntity;
import com.sjs.domain.strategy.model.valobj.RuleLogicCheckTypeVO;
import com.sjs.domain.strategy.service.armory.IStrategyDispatch;
import com.sjs.domain.strategy.service.rule.AbstractRaffleStrategy;
import com.sjs.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import com.sjs.domain.strategy.service.rule.filter.ILogicFilter;
import com.sjs.domain.strategy.service.rule.filter.factory.DefaultLogicFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@Service
public class DefaultRaffleStrategy extends AbstractRaffleStrategy {

    @Resource
    private DefaultLogicFactory logicFactory;

    public DefaultRaffleStrategy(IStrategyDispatch strategyDispatch, IStrategyRepository repository, DefaultChainFactory defaultChainFactory, DefaultLogicFactory logicFactory) {
        super(strategyDispatch, repository, defaultChainFactory);
        this.logicFactory = logicFactory;
    }



    @Override
    protected RuleActionEntity<RuleActionEntity.RaffleCenterEntity> doCheckRaffleCenterLogic(RaffleFactorEntity raffleFactorEntity, String[] ruleModel) {
        //1.判断是否有规则
        if(ruleModel == null || ruleModel.length == 0){
            return RuleActionEntity.<RuleActionEntity.RaffleCenterEntity>builder()
                    .code(RuleLogicCheckTypeVO.ALLOW.getCode())
                    .info(RuleLogicCheckTypeVO.ALLOW.getInfo())
                    .build();
        }

        //2.获取工厂策略
        Map<String, ILogicFilter<RuleActionEntity.RaffleCenterEntity>> stringILogicFilterMap = logicFactory.openLogicFilter();

        RuleActionEntity<RuleActionEntity.RaffleCenterEntity> ruleActionEntity = null;
        for (String rule : ruleModel) {
            ILogicFilter<RuleActionEntity.RaffleCenterEntity> logicFilter = stringILogicFilterMap.get(rule);
            RuleMatterEntity ruleMatterEntity = new RuleMatterEntity();
            ruleMatterEntity.setUserId(raffleFactorEntity.getUserId());
            ruleMatterEntity.setStrategyId(raffleFactorEntity.getStrategyId());
            ruleMatterEntity.setRuleModel(rule);
            ruleMatterEntity.setAwardId(raffleFactorEntity.getAwardId());

            ruleActionEntity = logicFilter.filter(ruleMatterEntity);
            //如果不放行则直接返回
            log.info("抽奖前规则过滤 userId: {} ruleModel: {} code: {} info: {}", raffleFactorEntity.getUserId(), ruleModel, ruleActionEntity.getCode(), ruleActionEntity.getInfo());
            if(!RuleLogicCheckTypeVO.ALLOW.getCode().equals(ruleActionEntity.getCode())){
                return ruleActionEntity;
            }
        }

        return ruleActionEntity;
    }
}
