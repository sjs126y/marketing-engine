package com.sjs.domain.strategy.service.rule.chain;

public interface ILogicChainArmory {
    ILogicChain next();

    ILogicChain appendNext(ILogicChain next);
}
