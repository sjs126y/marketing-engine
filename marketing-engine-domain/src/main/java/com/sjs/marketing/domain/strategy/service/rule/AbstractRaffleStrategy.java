package com.sjs.marketing.domain.strategy.service.rule;

import com.sjs.marketing.domain.strategy.adapter.repository.IStrategyRepository;
import com.sjs.marketing.domain.strategy.model.entity.RaffleAwardEntity;
import com.sjs.marketing.domain.strategy.model.entity.RaffleFactorEntity;
import com.sjs.marketing.domain.strategy.model.entity.StrategyAwardEntity;
import com.sjs.marketing.domain.strategy.service.IRaffleStrategy;
import com.sjs.marketing.domain.strategy.service.armory.IStrategyDispatch;
import com.sjs.marketing.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import com.sjs.marketing.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import com.sjs.marketing.types.enums.ResponseCode;
import com.sjs.marketing.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;

@Slf4j
public abstract class AbstractRaffleStrategy implements IRaffleStrategy {

    protected IStrategyDispatch strategyDispatch;

    protected IStrategyRepository repository;

    protected  DefaultChainFactory defaultChainFactory;

    protected DefaultTreeFactory defaultTreeFactory;

    public AbstractRaffleStrategy(IStrategyDispatch strategyDispatch, IStrategyRepository repository, DefaultChainFactory defaultChainFactory, DefaultTreeFactory defaultTreeFactory) {
        this.strategyDispatch = strategyDispatch;
        this.repository = repository;
        this.defaultChainFactory = defaultChainFactory;
        this.defaultTreeFactory = defaultTreeFactory;
    }

    @Override
    public RaffleAwardEntity performRaffle(RaffleFactorEntity raffleFactorEntity) {
        //1.参数校验
        String userId = raffleFactorEntity.getUserId();
        Long strategyId = raffleFactorEntity.getStrategyId();
        if(strategyId == null || StringUtils.isBlank(userId)){
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }

        //2.执行抽奖前规则过滤
        DefaultChainFactory.StrategyAwardVO chainStrategyAwardVO = raffleLogicChain(userId, strategyId);
        log.info("执行抽奖前规则过滤 chainStrategyAwardVO:{}", chainStrategyAwardVO);
        if(DefaultChainFactory.LogicModel.RULE_BLACKLIST.getCode().equals(chainStrategyAwardVO.getLogicModel())){
            return buildRaffleAwardEntity(strategyId, chainStrategyAwardVO.getAwardId(), null);
        }

        //3.执行抽奖策略树
        DefaultTreeFactory.StrategyAwardVO treeStrategyAwardVO = raffleLogicTree(userId, strategyId, chainStrategyAwardVO.getAwardId(), raffleFactorEntity.getEndDateTime());
        log.info("执行抽奖策略树 treeStrategyAwardVO:{}", treeStrategyAwardVO);

        //4.返回抽奖结果
        return buildRaffleAwardEntity(strategyId, treeStrategyAwardVO.getAwardId(), treeStrategyAwardVO.getAwardRuleValue());
    }

    private RaffleAwardEntity buildRaffleAwardEntity(Long strategyId, Integer awardId, String awardRuleValue) {
        StrategyAwardEntity strategyAward = repository.queryStrategyAwardEntity(strategyId, awardId);
        return RaffleAwardEntity.builder()
                .awardId(awardId)
                .awardTitle(strategyAward.getAwardTitle())
                .awardConfig(awardRuleValue)
                .sort(strategyAward.getSort())
                .build();
    }

    protected abstract DefaultChainFactory.StrategyAwardVO raffleLogicChain(String userId, Long strategyId);

    protected abstract DefaultTreeFactory.StrategyAwardVO raffleLogicTree(String userId, Long strategyId, Integer awardId, Date endDateTime);

}
