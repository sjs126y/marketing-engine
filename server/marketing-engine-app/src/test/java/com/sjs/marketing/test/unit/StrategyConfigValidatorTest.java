package com.sjs.marketing.test.unit;

import com.sjs.marketing.domain.strategy.adapter.repository.IStrategyRepository;
import com.sjs.marketing.domain.strategy.model.entity.StrategyAwardEntity;
import com.sjs.marketing.domain.strategy.model.entity.StrategyEntity;
import com.sjs.marketing.domain.strategy.model.valobj.ConfigViolation;
import com.sjs.marketing.domain.strategy.model.valobj.RuleLimitTypeVO;
import com.sjs.marketing.domain.strategy.model.valobj.RuleLogicCheckTypeVO;
import com.sjs.marketing.domain.strategy.model.valobj.RuleTreeNodeLineVO;
import com.sjs.marketing.domain.strategy.model.valobj.RuleTreeNodeVO;
import com.sjs.marketing.domain.strategy.model.valobj.RuleTreeVO;
import com.sjs.marketing.domain.strategy.service.rule.tree.ILogicTreeNode;
import com.sjs.marketing.domain.strategy.service.validate.StrategyConfigValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 配置校验器验证。
 *
 * <p>这套系统里规则、树、概率全部数据驱动，配错既不会编译失败也不会抛异常，
 * 只会「悄悄地按错的规则跑」。校验器的职责就是把这些静默错误变成发布期失败，
 * 下面每个用例对应一类真实踩过的坑。</p>
 *
 * @author marketing-engine contributors
 */
class StrategyConfigValidatorTest {

    private static final Long STRATEGY_ID = 100006L;

    private IStrategyRepository repository;

    private StrategyConfigValidator validator;

    @BeforeEach
    void setUp() {
        repository = mock(IStrategyRepository.class);
        validator = new StrategyConfigValidator();
        ReflectionTestUtils.setField(validator, "repository", repository);

        Map<String, ILogicTreeNode> nodeGroup = new HashMap<>();
        nodeGroup.put("rule_lock", stubNode());
        nodeGroup.put("rule_stock", stubNode());
        nodeGroup.put("rule_luck_award", stubNode());
        ReflectionTestUtils.setField(validator, "logicTreeNodeGroup", nodeGroup);
    }

    @Test
    @DisplayName("概率之和不为 1 时拒绝发布")
    void shouldRejectWhenRateNotNormalized() {
        // 种子数据里的真实问题：8 个奖品概率和只有 0.23
        when(repository.queryStrategyAwardList(STRATEGY_ID)).thenReturn(List.of(
                award(101, "0.1", 100, null),
                award(102, "0.13", 100, null)));

        List<ConfigViolation> violations = validator.validate(STRATEGY_ID);

        assertThat(violations)
                .anyMatch(v -> "RATE_NOT_NORMALIZED".equals(v.getCode()) && v.isError());
    }

    @Test
    @DisplayName("配了库存但没挂规则树时给出警告（抽中不扣库存等于无限发）")
    void shouldWarnWhenStockAwardHasNoTree() {
        when(repository.queryStrategyAwardList(STRATEGY_ID)).thenReturn(List.of(
                award(101, "1.0", 100, null)));

        List<ConfigViolation> violations = validator.validate(STRATEGY_ID);

        assertThat(violations)
                .anyMatch(v -> "AWARD_STOCK_WITHOUT_TREE".equals(v.getCode()));
    }

    @Test
    @DisplayName("配了库存但树里没有 rule_stock 节点时拒绝发布")
    void shouldRejectWhenTreeMissingStockNode() {
        when(repository.queryStrategyAwardList(STRATEGY_ID)).thenReturn(List.of(
                award(105, "1.0", 100, "tree_no_stock")));
        when(repository.queryRuleTreeVOByTreeId("tree_no_stock")).thenReturn(treeMissingStockNode());

        List<ConfigViolation> violations = validator.validate(STRATEGY_ID);

        assertThat(violations)
                .anyMatch(v -> "TREE_MISSING_STOCK_NODE".equals(v.getCode()) && v.isError());
    }

    @Test
    @DisplayName("规则树指向不存在的节点时拒绝发布")
    void shouldRejectWhenTreeEdgePointsToMissingNode() {
        when(repository.queryStrategyAwardList(STRATEGY_ID)).thenReturn(List.of(
                award(102, "1.0", 0, "tree_broken")));
        when(repository.queryRuleTreeVOByTreeId("tree_broken")).thenReturn(treeWithDanglingEdge());

        List<ConfigViolation> violations = validator.validate(STRATEGY_ID);

        assertThat(violations)
                .anyMatch(v -> "TREE_LINE_TO_MISSING".equals(v.getCode()) && v.isError());
    }

    @Test
    @DisplayName("规则树节点没有对应 Bean 时拒绝发布")
    void shouldRejectWhenNodeBeanMissing() {
        when(repository.queryStrategyAwardList(STRATEGY_ID)).thenReturn(List.of(
                award(102, "1.0", 0, "tree_unknown_key")));
        when(repository.queryRuleTreeVOByTreeId("tree_unknown_key")).thenReturn(treeWithUnknownKey());

        List<ConfigViolation> violations = validator.validate(STRATEGY_ID);

        assertThat(violations)
                .anyMatch(v -> "TREE_NODE_BEAN_MISSING".equals(v.getCode()) && v.isError());
    }

    @Test
    @DisplayName("权重档位配置的奖品都不在本策略下时拒绝发布")
    void shouldRejectWhenWeightGroupPicksNoAward() {
        when(repository.queryStrategyAwardList(STRATEGY_ID)).thenReturn(List.of(
                award(102, "1.0", 0, null)));

        StrategyEntity strategy = new StrategyEntity();
        ReflectionTestUtils.setField(strategy, "ruleModels", "rule_weight");
        when(repository.queryStrategyEntityByStrategyId(STRATEGY_ID)).thenReturn(strategy);

        com.sjs.marketing.domain.strategy.model.entity.StrategyRuleEntity rule =
                new com.sjs.marketing.domain.strategy.model.entity.StrategyRuleEntity();
        ReflectionTestUtils.setField(rule, "ruleModel", "rule_weight");
        // 档位只配了 999，而策略里只有 102
        ReflectionTestUtils.setField(rule, "ruleValue", "10:999");
        when(repository.queryStrategyRuleEntity(STRATEGY_ID, "rule_weight")).thenReturn(rule);

        List<ConfigViolation> violations = validator.validate(STRATEGY_ID);

        assertThat(violations)
                .anyMatch(v -> "WEIGHT_GROUP_EMPTY".equals(v.getCode()));
    }

    @Test
    @DisplayName("配置完全正确时没有任何 ERROR")
    void shouldPassWhenConfigIsHealthy() {
        when(repository.queryStrategyAwardList(STRATEGY_ID)).thenReturn(List.of(
                award(105, "1.0", 100, "tree_ok")));
        when(repository.queryRuleTreeVOByTreeId("tree_ok")).thenReturn(treeHealthy());

        List<ConfigViolation> violations = validator.validate(STRATEGY_ID);

        assertThat(violations).noneMatch(ConfigViolation::isError);
    }

    // ---------- 构造工具 ----------

    private StrategyAwardEntity award(int awardId, String rate, int count, String treeId) {
        StrategyAwardEntity entity = new StrategyAwardEntity();
        entity.setStrategyId(STRATEGY_ID);
        entity.setAwardId(awardId);
        entity.setAwardRate(new BigDecimal(rate));
        entity.setAwardCount(count);
        entity.setRuleModels(treeId);
        return entity;
    }

    private ILogicTreeNode stubNode() {
        return (userId, strategyId, awardId, ruleValue, date) -> null;
    }

    private RuleTreeVO treeHealthy() {
        List<RuleTreeNodeVO> nodes = new ArrayList<>();
        nodes.add(node("rule_stock", line("rule_stock", "rule_luck_award", RuleLogicCheckTypeVO.ALLOW)));
        nodes.add(node("rule_luck_award", null));
        return tree("rule_stock", nodes);
    }

    private RuleTreeVO treeMissingStockNode() {
        List<RuleTreeNodeVO> nodes = new ArrayList<>();
        nodes.add(node("rule_lock", line("rule_lock", "rule_luck_award", RuleLogicCheckTypeVO.ALLOW)));
        nodes.add(node("rule_luck_award", null));
        return tree("rule_lock", nodes);
    }

    private RuleTreeVO treeWithDanglingEdge() {
        List<RuleTreeNodeVO> nodes = new ArrayList<>();
        nodes.add(node("rule_stock", line("rule_stock", "rule_not_exist", RuleLogicCheckTypeVO.ALLOW)));
        return tree("rule_stock", nodes);
    }

    private RuleTreeVO treeWithUnknownKey() {
        List<RuleTreeNodeVO> nodes = new ArrayList<>();
        nodes.add(node("rule_unknown", null));
        return tree("rule_unknown", nodes);
    }

    private RuleTreeNodeLineVO line(String from, String to, RuleLogicCheckTypeVO checkType) {
        return RuleTreeNodeLineVO.builder()
                .treeId("tree")
                .ruleNodeFrom(from)
                .ruleNodeTo(to)
                .ruleLimitType(RuleLimitTypeVO.EQUAL)
                .ruleLimitValue(checkType)
                .build();
    }

    private RuleTreeNodeVO node(String ruleKey, RuleTreeNodeLineVO line) {
        List<RuleTreeNodeLineVO> lines = new ArrayList<>();
        if (line != null) {
            lines.add(line);
        }
        return RuleTreeNodeVO.builder()
                .treeId("tree")
                .ruleKey(ruleKey)
                .ruleDesc(ruleKey)
                .treeNodeLineVOList(lines)
                .build();
    }

    private RuleTreeVO tree(String root, List<RuleTreeNodeVO> nodes) {
        Map<String, RuleTreeNodeVO> map = new HashMap<>();
        for (RuleTreeNodeVO node : nodes) {
            map.put(node.getRuleKey(), node);
        }
        return RuleTreeVO.builder()
                .treeId("tree")
                .treeName("测试树")
                .treeNodeMap(map)
                .treeRootRuleNode(root)
                .build();
    }

}
