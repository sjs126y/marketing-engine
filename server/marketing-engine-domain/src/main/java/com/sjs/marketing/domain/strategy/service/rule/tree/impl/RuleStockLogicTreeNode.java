package com.sjs.marketing.domain.strategy.service.rule.tree.impl;

import com.sjs.marketing.domain.strategy.adapter.repository.IStrategyRepository;
import com.sjs.marketing.domain.strategy.model.valobj.RuleLogicCheckTypeVO;
import com.sjs.marketing.domain.strategy.model.entity.StrategyAwardEntity;
import com.sjs.marketing.domain.strategy.model.valobj.StrategyAwardStockKeyVO;
import com.sjs.marketing.domain.strategy.service.armory.IStrategyDispatch;
import com.sjs.marketing.domain.strategy.service.rule.tree.ILogicTreeNode;
import com.sjs.marketing.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Date;

@Slf4j
@Component("rule_stock")
public class RuleStockLogicTreeNode implements ILogicTreeNode {
    
    @Resource
    private IStrategyDispatch strategyDispatch;
    @Resource
    private IStrategyRepository repository;
    
    @Override
    public DefaultTreeFactory.TreeActionEntity logic(String userId, Long strategyId, Integer awardId, String ruleValue, Date endDateTime) {
        log.info("规则过滤-库存扣减 userId:{} strategyId:{} awardId:{}", userId, strategyId, awardId);

        //扣减库存
        Integer surplus = strategyDispatch.subtractionAwardStock(strategyId, awardId, endDateTime);

        //扣减成功，发送消息到队列
        if (surplus != null) {
            log.info("规则过滤-库存扣减成功 userId:{} strategyId:{} awardId:{}", userId, strategyId, awardId);
            // 带上「扣减后的累计已扣数」，落库时按绝对量回写，重复消费安全
            StrategyAwardEntity awardEntity = repository.queryStrategyAwardEntity(strategyId, awardId);
            int total = awardEntity == null || awardEntity.getAwardCount() == null ? 0 : awardEntity.getAwardCount();

            StrategyAwardStockKeyVO strategyAwardStockKeyVO = StrategyAwardStockKeyVO.builder()
                    .strategyId(strategyId)
                    .awardId(awardId)
                    .consumedAfter(total - surplus)
                    .build();
            repository.awardStockConsumeSendQueue(strategyAwardStockKeyVO);

            return DefaultTreeFactory.TreeActionEntity.builder()
                    .ruleLogicCheckType(RuleLogicCheckTypeVO.TAKE_OVER)
                    .strategyAwardVO(DefaultTreeFactory.StrategyAwardVO.builder()
                            .awardId(awardId)
                            .awardRuleValue(ruleValue)
                            .build())
                    .build();
        }

        //扣减失败,放行给兜底节点
        log.info("规则过滤-库存扣减失败 userId:{} strategyId:{} awardId:{}", userId, strategyId, awardId);
        return DefaultTreeFactory.TreeActionEntity.builder()
                .ruleLogicCheckType(RuleLogicCheckTypeVO.TAKE_OVER)
                .build();
    }
}
