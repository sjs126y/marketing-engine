package com.sjs.domain.activity.service.rule;

public interface IActionChainArmory {

    IActionChain next();

    IActionChain appendNext(IActionChain actionChain);

}
