package com.sjs.domain.strategy.service.rule.chain;

import com.sjs.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import com.sjs.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;

public interface ILogicChain extends ILogicChainArmory{

    DefaultChainFactory.StrategyAwardVO logic(String userId, Long strategyId);

}
