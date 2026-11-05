package com.sjs.marketing.domain.strategy.service.rule.tree.factory.engine.impl;

import com.sjs.marketing.domain.strategy.model.valobj.RuleLogicCheckTypeVO;
import com.sjs.marketing.domain.strategy.model.valobj.RuleTreeNodeLineVO;
import com.sjs.marketing.domain.strategy.model.valobj.RuleTreeNodeVO;
import com.sjs.marketing.domain.strategy.model.valobj.RuleTreeVO;
import com.sjs.marketing.domain.strategy.service.rule.tree.ILogicTreeNode;
import com.sjs.marketing.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import com.sjs.marketing.domain.strategy.service.rule.tree.factory.engine.IDecisionTreeEngine;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
public class DecisionTreeEngine implements IDecisionTreeEngine {

    private final Map<String, ILogicTreeNode> logicTreeNodeGroup;

    private final RuleTreeVO ruleTreeVO;

    public DecisionTreeEngine(Map<String, ILogicTreeNode> logicTreeNodeGroup, RuleTreeVO ruleTreeVO) {
        this.logicTreeNodeGroup = logicTreeNodeGroup;
        this.ruleTreeVO = ruleTreeVO;
    }

    @Override
    public DefaultTreeFactory.StrategyAwardVO process(String userId, Long strategyId, Integer awardId, Date date) {
        DefaultTreeFactory.StrategyAwardVO strategyAwardVO = null;

        //获得基础信息
        String nextNode = ruleTreeVO.getTreeRootRuleNode();
        Map<String, RuleTreeNodeVO> treeNodeMap = ruleTreeVO.getTreeNodeMap();

        //获得根节点
        RuleTreeNodeVO ruleTreeNodeVO = treeNodeMap.get(nextNode);
        //开始循环执行
        while (nextNode != null) {
            ILogicTreeNode logicTreeNode = logicTreeNodeGroup.get(ruleTreeNodeVO.getRuleKey());

            //获取规则值
            String ruleValue = ruleTreeNodeVO.getRuleValue();

            DefaultTreeFactory.TreeActionEntity treeActionEntity = logicTreeNode.logic(userId, strategyId, awardId, ruleValue, date);
            RuleLogicCheckTypeVO ruleLogicCheckType = treeActionEntity.getRuleLogicCheckType();
            strategyAwardVO = treeActionEntity.getStrategyAwardVO();

            log.info("决策树引擎【{}】treeId:{} node:{} code:{}", ruleTreeVO.getTreeName(), ruleTreeVO.getTreeId(), nextNode, ruleLogicCheckType.getCode());

            // 获取下个节点
            nextNode = nextNode(ruleLogicCheckType.getCode(), ruleTreeNodeVO.getTreeNodeLineVOList());
            ruleTreeNodeVO = treeNodeMap.get(nextNode);
        }

        return strategyAwardVO;
    }


    public String nextNode(String matterValue, List<RuleTreeNodeLineVO> treeNodeLineVOList) {
        if (null == treeNodeLineVOList || treeNodeLineVOList.isEmpty()) return null;
        for (RuleTreeNodeLineVO nodeLine : treeNodeLineVOList) {
            if (decisionLogic(matterValue, nodeLine)) {
                return nodeLine.getRuleNodeTo();
            }
        }
        return null;
    }

    public boolean decisionLogic(String matterValue, RuleTreeNodeLineVO nodeLine) {
        switch (nodeLine.getRuleLimitType()) {
            case EQUAL:
                return matterValue.equals(nodeLine.getRuleLimitValue().getCode());
            // 以下规则暂时不需要实现
            case GT:
            case LT:
            case GE:
            case LE:
            default:
                return false;
        }
    }
}
