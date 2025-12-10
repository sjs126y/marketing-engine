package com.sjs.domain.strategy.service.raffle;

import com.sjs.domain.strategy.adapter.repository.IStrategyRepository;
import com.sjs.domain.strategy.model.entity.StrategyAwardEntity;
import com.sjs.domain.strategy.model.valobj.RuleTreeVO;
import com.sjs.domain.strategy.model.valobj.StrategyAwardRuleModelVO;
import com.sjs.domain.strategy.model.valobj.StrategyAwardStockKeyVO;
import com.sjs.domain.strategy.service.IRaffleAward;
import com.sjs.domain.strategy.service.IRaffleStock;
import com.sjs.domain.strategy.service.armory.IStrategyDispatch;
import com.sjs.domain.strategy.service.rule.AbstractRaffleStrategy;
import com.sjs.domain.strategy.service.rule.chain.ILogicChain;
import com.sjs.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import com.sjs.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import com.sjs.domain.strategy.service.rule.tree.factory.engine.IDecisionTreeEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;


@Slf4j
@Service
public class DefaultRaffleStrategy extends AbstractRaffleStrategy implements IRaffleStock, IRaffleAward {


    public DefaultRaffleStrategy(IStrategyDispatch strategyDispatch, IStrategyRepository repository, DefaultChainFactory defaultChainFactory, DefaultTreeFactory defaultTreeFactory) {
        super(strategyDispatch, repository, defaultChainFactory, defaultTreeFactory);
    }

    @Override
    protected DefaultChainFactory.StrategyAwardVO raffleLogicChain(String userId, Long strategyId) {
        ILogicChain logicChain = defaultChainFactory.openLogicChain(strategyId);
        return logicChain.logic(userId, strategyId);
    }

    @Override
    protected DefaultTreeFactory.StrategyAwardVO raffleLogicTree(String userId, Long strategyId, Integer awardId) {
        //1.先创建抽奖树
        StrategyAwardRuleModelVO strategyAwardRuleModelVO = repository.queryStrategyAwardRuleModelVO(strategyId, awardId);
        if(strategyAwardRuleModelVO == null) {
            return DefaultTreeFactory.StrategyAwardVO.builder().awardId(awardId).build();
        }
        RuleTreeVO ruleTreeVO = repository.queryRuleTreeVOByTreeId(strategyAwardRuleModelVO.getRuleModels());
        if (null == ruleTreeVO) {
            throw new RuntimeException("存在抽奖策略配置的规则模型 Key，未在库表 rule_tree、rule_tree_node、rule_tree_line 配置对应的规则树信息 " + strategyAwardRuleModelVO.getRuleModels());
        }

        IDecisionTreeEngine decisionTreeEngine = defaultTreeFactory.openLogicTree(ruleTreeVO);
        //2.执行抽奖树
        return decisionTreeEngine.process(userId, strategyId, awardId);
    }

    @Override
    public StrategyAwardStockKeyVO takeQueueValue() throws InterruptedException {
        return repository.takeQueueValue();
    }

    @Override
    public void updateStrategyAwardStock(Long strategyId, Integer awardId) {
        repository.updateStrategyAwardStock(strategyId, awardId);
    }

    @Override
    public List<StrategyAwardEntity> queryStrategyAwardList(Long strategyId) {
        return repository.queryStrategyAwardList(strategyId);
    }
}
