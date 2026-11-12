package com.sjs.marketing.test.unit;

import com.sjs.marketing.domain.strategy.model.valobj.RuleLimitTypeVO;
import com.sjs.marketing.domain.strategy.model.valobj.RuleLogicCheckTypeVO;
import com.sjs.marketing.domain.strategy.model.valobj.RuleTreeNodeLineVO;
import com.sjs.marketing.domain.strategy.model.valobj.RuleTreeNodeVO;
import com.sjs.marketing.domain.strategy.model.valobj.RuleTreeVO;
import com.sjs.marketing.domain.strategy.service.rule.tree.ILogicTreeNode;
import com.sjs.marketing.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import com.sjs.marketing.domain.strategy.service.rule.tree.factory.engine.impl.DecisionTreeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 决策树引擎的走线验证。
 *
 * <p>{@link DecisionTreeEngine} 只依赖「节点 Bean 映射 + 树 VO」，
 * 是纯粹的图遍历逻辑，因此可以用假的节点实现完整覆盖走线规则：</p>
 * <ul>
 *   <li>ALLOW（0000）走放行边；</li>
 *   <li>TAKE_OVER（0001）走接管边，通常通向兜底；</li>
 *   <li>没有匹配的边时循环结束，返回最后一个节点给出的奖品。</li>
 * </ul>
 *
 * @author marketing-engine contributors
 */
class DecisionTreeEngineTest {

    @Test
    @DisplayName("锁节点放行时走到库存节点，返回原奖品")
    void shouldFollowAllowEdgeToStock() {
        Map<String, ILogicTreeNode> nodes = new HashMap<>();
        nodes.put("rule_lock", allowNode("rule_lock"));
        nodes.put("rule_stock", takeOverNode("rule_stock", 105));

        RuleTreeVO tree = tree("rule_lock", List.of(
                node("rule_lock", "3", line("rule_lock", "rule_stock", RuleLogicCheckTypeVO.ALLOW)),
                node("rule_stock", null, line("rule_stock", "rule_luck_award", RuleLogicCheckTypeVO.ALLOW)),
                node("rule_luck_award", null, null)));

        DefaultTreeFactory.StrategyAwardVO result = new DecisionTreeEngine(nodes, tree)
                .process("user-1", 100006L, 105, new Date());

        assertThat(result).isNotNull();
        assertThat(result.getAwardId()).isEqualTo(105);
    }

    @Test
    @DisplayName("锁节点接管时直接走兜底，奖品被替换")
    void shouldFallbackWhenLockTakesOver() {
        Map<String, ILogicTreeNode> nodes = new HashMap<>();
        nodes.put("rule_lock", takeOverNode("rule_lock", 0));
        nodes.put("rule_luck_award", awardNode("rule_luck_award", 101));

        RuleTreeVO tree = tree("rule_lock", List.of(
                node("rule_lock", "3", line("rule_lock", "rule_luck_award", RuleLogicCheckTypeVO.TAKE_OVER)),
                node("rule_luck_award", "101:1,100", null)));

        DefaultTreeFactory.StrategyAwardVO result = new DecisionTreeEngine(nodes, tree)
                .process("user-2", 100006L, 105, new Date());

        assertThat(result).isNotNull();
        assertThat(result.getAwardId()).isEqualTo(101);
    }

    @Test
    @DisplayName("没有匹配的出边时循环结束，返回该节点的结果")
    void shouldStopWhenNoEdgeMatches() {
        Map<String, ILogicTreeNode> nodes = new HashMap<>();
        nodes.put("rule_stock", takeOverNode("rule_stock", 102));

        // 库存节点返回 TAKE_OVER，但只配了 ALLOW 边 → 匹配不上
        RuleTreeVO tree = tree("rule_stock", List.of(
                node("rule_stock", null, line("rule_stock", "rule_luck_award", RuleLogicCheckTypeVO.ALLOW)),
                node("rule_luck_award", null, null)));

        DefaultTreeFactory.StrategyAwardVO result = new DecisionTreeEngine(nodes, tree)
                .process("user-3", 100006L, 102, new Date());

        assertThat(result).isNotNull();
        assertThat(result.getAwardId()).isEqualTo(102);
    }

    @Test
    @DisplayName("节点返回空奖品且无出边时结果为 null，调用方必须防御")
    void shouldReturnNullWhenNodeProducesNoAward() {
        Map<String, ILogicTreeNode> nodes = new HashMap<>();
        nodes.put("rule_stock", failedStockNode());

        RuleTreeVO tree = tree("rule_stock", List.of(
                node("rule_stock", null, line("rule_stock", "rule_luck_award", RuleLogicCheckTypeVO.ALLOW)),
                node("rule_luck_award", null, null)));

        DefaultTreeFactory.StrategyAwardVO result = new DecisionTreeEngine(nodes, tree)
                .process("user-4", 100006L, 103, new Date());

        // 这正是「库存耗尽但兜底边配错」时的表现，验证引擎不会自己兜底
        assertThat(result).isNull();
    }

    // ---------- 构造工具 ----------

    private ILogicTreeNode allowNode(String key) {
        return (userId, strategyId, awardId, ruleValue, date) -> DefaultTreeFactory.TreeActionEntity.builder()
                .ruleLogicCheckType(RuleLogicCheckTypeVO.ALLOW)
                .build();
    }

    private ILogicTreeNode takeOverNode(String key, int awardId) {
        return (userId, strategyId, awardIdParam, ruleValue, date) -> DefaultTreeFactory.TreeActionEntity.builder()
                .ruleLogicCheckType(RuleLogicCheckTypeVO.TAKE_OVER)
                .strategyAwardVO(DefaultTreeFactory.StrategyAwardVO.builder()
                        .awardId(awardId)
                        .awardRuleValue(ruleValue)
                        .build())
                .build();
    }

    private ILogicTreeNode awardNode(String key, int fixedAwardId) {
        return (userId, strategyId, awardId, ruleValue, date) -> DefaultTreeFactory.TreeActionEntity.builder()
                .ruleLogicCheckType(RuleLogicCheckTypeVO.ALLOW)
                .strategyAwardVO(DefaultTreeFactory.StrategyAwardVO.builder()
                        .awardId(fixedAwardId)
                        .awardRuleValue(ruleValue)
                        .build())
                .build();
    }

    private ILogicTreeNode failedStockNode() {
        return (userId, strategyId, awardId, ruleValue, date) -> DefaultTreeFactory.TreeActionEntity.builder()
                .ruleLogicCheckType(RuleLogicCheckTypeVO.TAKE_OVER)
                .strategyAwardVO(null)
                .build();
    }

    private RuleTreeNodeLineVO line(String from, String to, RuleLogicCheckTypeVO checkType) {
        return RuleTreeNodeLineVO.builder()
                .treeId("tree-test")
                .ruleNodeFrom(from)
                .ruleNodeTo(to)
                .ruleLimitType(RuleLimitTypeVO.EQUAL)
                .ruleLimitValue(checkType)
                .build();
    }

    private RuleTreeNodeVO node(String ruleKey, String ruleValue, RuleTreeNodeLineVO line) {
        List<RuleTreeNodeLineVO> lines = new ArrayList<>();
        if (line != null) {
            lines.add(line);
        }
        return RuleTreeNodeVO.builder()
                .treeId("tree-test")
                .ruleKey(ruleKey)
                .ruleDesc(ruleKey)
                .ruleValue(ruleValue)
                .treeNodeLineVOList(lines)
                .build();
    }

    private RuleTreeVO tree(String root, List<RuleTreeNodeVO> nodes) {
        Map<String, RuleTreeNodeVO> map = new HashMap<>();
        for (RuleTreeNodeVO node : nodes) {
            map.put(node.getRuleKey(), node);
        }
        return RuleTreeVO.builder()
                .treeId("tree-test")
                .treeName("测试树")
                .treeDesc("测试")
                .treeRootRuleNode(root)
                .treeNodeMap(map)
                .build();
    }

}
