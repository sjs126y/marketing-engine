package com.sjs.domain.strategy.service.raffle;

import com.sjs.domain.strategy.adapter.repository.IStrategyRepository;
import com.sjs.domain.strategy.model.entity.RaffleFactorEntity;
import com.sjs.domain.strategy.model.entity.RuleActionEntity;
import com.sjs.domain.strategy.model.entity.RuleMatterEntity;
import com.sjs.domain.strategy.model.valobj.RuleLogicCheckTypeVO;
import com.sjs.domain.strategy.service.armory.IStrategyDispatch;
import com.sjs.domain.strategy.service.rule.ILogicFilter;
import com.sjs.domain.strategy.service.rule.factory.DefaultLogicFactory;
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
public class DefaultRaffleStrategy extends AbstractRaffleStrategy{

    @Resource
    private DefaultLogicFactory logicFactory;

    public DefaultRaffleStrategy(IStrategyDispatch strategyDispatch, IStrategyRepository repository) {
        super(strategyDispatch, repository);
    }

    @Override
    protected RuleActionEntity<RuleActionEntity.RaffleBeforeEntity> doCheckRaffleBeforeLogic(RaffleFactorEntity raffleFactorEntity, String... ruleModel) {
        if (ruleModel == null || 0 == ruleModel.length) return RuleActionEntity.<RuleActionEntity.RaffleBeforeEntity>builder()
                .code(RuleLogicCheckTypeVO.ALLOW.getCode())
                .info(RuleLogicCheckTypeVO.ALLOW.getInfo())
                .build();

        //1.先获取工厂策略
        Map<String, ILogicFilter<RuleActionEntity.RaffleBeforeEntity>> stringILogicFilterMap = logicFactory.openLogicFilter();

        //2.先执行黑名单过滤
        String ruleBackList = Arrays.stream(ruleModel)
                .filter(str -> str.contains(DefaultLogicFactory.LogicModel.RULE_BLACKLIST.getCode()))
                .findFirst()
                .orElse(null);

        if(StringUtils.isNotBlank(ruleBackList)){
            ILogicFilter<RuleActionEntity.RaffleBeforeEntity> logicFilter = stringILogicFilterMap.get(DefaultLogicFactory.LogicModel.RULE_BLACKLIST.getCode());
            RuleMatterEntity ruleMatterEntity = new RuleMatterEntity();
            ruleMatterEntity.setUserId(raffleFactorEntity.getUserId());
            ruleMatterEntity.setStrategyId(raffleFactorEntity.getStrategyId());
            ruleMatterEntity.setRuleModel(ruleBackList);
            ruleMatterEntity.setAwardId(ruleMatterEntity.getAwardId());

            RuleActionEntity<RuleActionEntity.RaffleBeforeEntity> ruleActionEntity = logicFilter.filter(ruleMatterEntity);
            //如果不放行则直接返回
            if(!RuleLogicCheckTypeVO.ALLOW.getCode().equals(ruleActionEntity.getCode())){
                return ruleActionEntity;
            }
        }

        //3.执行其他过滤
        List<String> ruleList = Arrays.stream(ruleModel)
                .filter(str -> !str.equals(DefaultLogicFactory.LogicModel.RULE_BLACKLIST.getCode()))
                .collect(Collectors.toList());
        RuleActionEntity<RuleActionEntity.RaffleBeforeEntity> ruleActionEntity = null;
        for (String rule : ruleList) {
            ILogicFilter<RuleActionEntity.RaffleBeforeEntity> logicFilter = stringILogicFilterMap.get(rule);
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
