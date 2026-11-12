package com.sjs.marketing.domain.strategy.service.validate;

import com.sjs.marketing.domain.strategy.adapter.repository.IStrategyRepository;
import com.sjs.marketing.domain.strategy.model.entity.StrategyAwardEntity;
import com.sjs.marketing.domain.strategy.model.entity.StrategyEntity;
import com.sjs.marketing.domain.strategy.model.entity.StrategyRuleEntity;
import com.sjs.marketing.domain.strategy.model.valobj.ConfigViolation;
import com.sjs.marketing.domain.strategy.model.valobj.RuleTreeNodeLineVO;
import com.sjs.marketing.domain.strategy.model.valobj.RuleTreeNodeVO;
import com.sjs.marketing.domain.strategy.model.valobj.RuleTreeVO;
import com.sjs.marketing.domain.strategy.service.rule.tree.ILogicTreeNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 策略配置校验器。
 *
 * <p>这套系统里规则、树、概率全部由数据驱动，配错不会编译失败也不会抛异常，
 * 只会「悄悄地按错的规则跑」——例如奖品永远发不出去、库存耗尽时兜底失效。
 * 校验器把这类静默错误变成**发布期失败**：</p>
 *
 * <ol>
 *   <li>概率归一：各奖品中奖概率之和必须为 1；</li>
 *   <li>权重档位：每个档位的奖品子集不能为空；</li>
 *   <li>树结构：根节点存在、每条边两端都在节点表里、无环、无孤立节点；</li>
 *   <li>节点 Bean：rule_key 必须能找到对应的 {@link ILogicTreeNode} 实现；</li>
 *   <li>库存节点：配了库存的奖品必须挂 rule_stock，否则会无限发奖。</li>
 * </ol>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Service
public class StrategyConfigValidator {

    /** 概率之和允许的误差 */
    private static final BigDecimal RATE_TOLERANCE = new BigDecimal("0.000001");

    /** 判断「有库存」时使用的阈值：总量大于该值即认为该奖品需要扣库存 */
    private static final int STOCK_THRESHOLD = 0;

    @Resource
    private IStrategyRepository repository;

    @Resource
    private Map<String, ILogicTreeNode> logicTreeNodeGroup;

    /**
     * 校验一个策略的完整配置。
     *
     * @param strategyId 策略 ID
     * @return 发现的问题列表；空列表表示可以发布
     */
    public List<ConfigViolation> validate(Long strategyId) {
        List<ConfigViolation> violations = new ArrayList<>();
        String target = "strategyId=" + strategyId;

        List<StrategyAwardEntity> awards = repository.queryStrategyAwardList(strategyId);
        if (awards == null || awards.isEmpty()) {
            violations.add(ConfigViolation.error("AWARD_EMPTY", target, "策略下没有任何奖品，无法装配概率表"));
            return violations;
        }

        validateRateSum(strategyId, awards, violations);
        validateAwardRate(strategyId, awards, violations);
        validateWeight(strategyId, violations);
        validateTrees(strategyId, awards, violations);

        return violations;
    }

    /**
     * 概率之和必须为 1（允许 1e-6 误差）。
     *
     * <p>装配算法按「总概率 / 最小概率」向上取整得到区间长度，
     * 只有归一后各奖品的槽位数才正好填满区间，否则 CEILING 会把分布抹平。</p>
     */
    private void validateRateSum(Long strategyId, List<StrategyAwardEntity> awards, List<ConfigViolation> violations) {
        BigDecimal sum = BigDecimal.ZERO;
        for (StrategyAwardEntity award : awards) {
            if (award.getAwardRate() != null) {
                sum = sum.add(award.getAwardRate());
            }
        }
        if (sum.subtract(BigDecimal.ONE).abs().compareTo(RATE_TOLERANCE) > 0) {
            violations.add(ConfigViolation.error("RATE_NOT_NORMALIZED", "strategyId=" + strategyId,
                    "奖品概率之和为 " + sum.stripTrailingZeros().toPlainString()
                            + "，应为 1；否则按最小概率展开时取整会让实际分布偏离配置"));
        }
    }

    private void validateAwardRate(Long strategyId, List<StrategyAwardEntity> awards, List<ConfigViolation> violations) {
        for (StrategyAwardEntity award : awards) {
            if (award.getAwardRate() == null || award.getAwardRate().compareTo(BigDecimal.ZERO) <= 0) {
                violations.add(ConfigViolation.error("AWARD_RATE_INVALID",
                        "strategyId=" + strategyId + " award=" + award.getAwardId(),
                        "奖品概率必须大于 0"));
            }
        }
    }

    /**
     * 权重档位：每个档位必须能筛出非空奖品子集。
     */
    private void validateWeight(Long strategyId, List<ConfigViolation> violations) {
        StrategyEntity strategy = repository.queryStrategyEntityByStrategyId(strategyId);
        if (strategy == null || strategy.getRuleWeight() == null) {
            return;
        }

        StrategyRuleEntity rule = repository.queryStrategyRuleEntity(strategyId, strategy.getRuleWeight());
        if (rule == null || rule.getRuleValue() == null) {
            violations.add(ConfigViolation.error("WEIGHT_RULE_MISSING", "strategyId=" + strategyId,
                    "策略声明了 rule_weight，但 strategy_rule 中没有对应规则值"));
            return;
        }

        Map<String, List<Integer>> groups = rule.getRuleWeightValues();
        if (groups == null || groups.isEmpty()) {
            return;
        }

        List<StrategyAwardEntity> awards = repository.queryStrategyAwardList(strategyId);
        Set<Integer> awardIds = new HashSet<>();
        for (StrategyAwardEntity award : awards) {
            awardIds.add(award.getAwardId());
        }

        for (Map.Entry<String, List<Integer>> entry : groups.entrySet()) {
            List<Integer> configured = entry.getValue();
            boolean any = false;
            for (Integer awardId : configured) {
                if (awardIds.contains(awardId)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                violations.add(ConfigViolation.error("WEIGHT_GROUP_EMPTY",
                        "strategyId=" + strategyId + " group=" + entry.getKey(),
                        "该权重档位配置的奖品都不在本策略下，装配出的概率表为空"));
            }
        }
    }

    /**
     * 树结构 + 节点 Bean + 库存节点约束。
     */
    private void validateTrees(Long strategyId, List<StrategyAwardEntity> awards, List<ConfigViolation> violations) {
        for (StrategyAwardEntity award : awards) {
            String treeId = award.getRuleModels();
            if (treeId == null || treeId.isEmpty()) {
                // 没挂树的奖品不会扣库存，若它有库存就是隐患
                if (award.getAwardCount() != null && award.getAwardCount() > STOCK_THRESHOLD) {
                    violations.add(ConfigViolation.warn("AWARD_STOCK_WITHOUT_TREE",
                            "strategyId=" + strategyId + " award=" + award.getAwardId(),
                            "奖品配了库存但没挂规则树，抽中后不会扣减库存，等于无限发"));
                }
                continue;
            }

            RuleTreeVO tree = repository.queryRuleTreeVOByTreeId(treeId);
            String target = "strategyId=" + strategyId + " award=" + award.getAwardId() + " tree=" + treeId;
            if (tree == null) {
                violations.add(ConfigViolation.error("TREE_MISSING", target,
                        "strategy_award.rule_models 指向的规则树不存在"));
                continue;
            }

            validateTreeStructure(tree, target, violations);
            validateTreeNodesExist(tree, target, violations);

            if (award.getAwardCount() != null && award.getAwardCount() > STOCK_THRESHOLD
                    && !containsRuleKey(tree, "rule_stock")) {
                violations.add(ConfigViolation.error("TREE_MISSING_STOCK_NODE", target,
                        "奖品配了库存，但规则树里没有 rule_stock 节点，抽中后不会扣库存"));
            }
        }
    }

    /**
     * 根节点存在、边两端存在、无环、无孤立节点。
     */
    private void validateTreeStructure(RuleTreeVO tree, String target, List<ConfigViolation> violations) {
        Map<String, RuleTreeNodeVO> nodes = tree.getTreeNodeMap();
        String root = tree.getTreeRootRuleNode();

        if (nodes == null || nodes.isEmpty()) {
            violations.add(ConfigViolation.error("TREE_EMPTY", target, "规则树没有任何节点"));
            return;
        }
        if (root == null || !nodes.containsKey(root)) {
            violations.add(ConfigViolation.error("TREE_ROOT_MISSING", target,
                    "根节点 " + root + " 不在节点表中"));
            return;
        }

        Set<String> pointedTo = new HashSet<>();
        pointedTo.add(root);

        for (RuleTreeNodeVO node : nodes.values()) {
            if (node.getTreeNodeLineVOList() == null) {
                continue;
            }
            for (RuleTreeNodeLineVO line : node.getTreeNodeLineVOList()) {
                String from = line.getRuleNodeFrom();
                String to = line.getRuleNodeTo();
                if (!nodes.containsKey(from)) {
                    violations.add(ConfigViolation.error("TREE_LINE_FROM_MISSING", target,
                            "连线的起点节点 " + from + " 不存在"));
                }
                if (!nodes.containsKey(to)) {
                    violations.add(ConfigViolation.error("TREE_LINE_TO_MISSING", target,
                            "连线的终点节点 " + to + " 不存在"));
                }
                pointedTo.add(to);
            }
        }

        for (String key : nodes.keySet()) {
            if (!pointedTo.contains(key)) {
                violations.add(ConfigViolation.warn("TREE_ORPHAN_NODE", target + " node=" + key,
                        "该节点没有任何入边，永远不会被执行"));
            }
        }

        if (hasCycle(nodes, root)) {
            violations.add(ConfigViolation.error("TREE_CYCLE", target, "规则树存在环，执行时可能死循环"));
        }
    }

    /**
     * 每个节点的 rule_key 都必须有对应的 Bean 实现。
     */
    private void validateTreeNodesExist(RuleTreeVO tree, String target, List<ConfigViolation> violations) {
        for (RuleTreeNodeVO node : tree.getTreeNodeMap().values()) {
            String ruleKey = node.getRuleKey();
            if (ruleKey == null || !logicTreeNodeGroup.containsKey(ruleKey)) {
                violations.add(ConfigViolation.error("TREE_NODE_BEAN_MISSING", target + " node=" + ruleKey,
                        "没有注册名为 " + ruleKey + " 的 ILogicTreeNode 实现"));
            }
        }
    }

    private boolean containsRuleKey(RuleTreeVO tree, String ruleKey) {
        for (RuleTreeNodeVO node : tree.getTreeNodeMap().values()) {
            if (ruleKey.equals(node.getRuleKey())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 用 DFS 检测环（只看可达部分）。
     */
    private boolean hasCycle(Map<String, RuleTreeNodeVO> nodes, String root) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            String current = stack.peek();
            if (!visiting.contains(current)) {
                visiting.add(current);
            }
            boolean pushed = false;
            RuleTreeNodeVO node = nodes.get(current);
            if (node != null && node.getTreeNodeLineVOList() != null) {
                for (RuleTreeNodeLineVO line : node.getTreeNodeLineVOList()) {
                    String to = line.getRuleNodeTo();
                    if (to == null || !nodes.containsKey(to)) {
                        continue;
                    }
                    if (visiting.contains(to) && !visited.contains(to)) {
                        return true;
                    }
                    if (!visited.contains(to) && !visiting.contains(to)) {
                        stack.push(to);
                        pushed = true;
                        break;
                    }
                }
            }
            if (!pushed) {
                stack.pop();
                visiting.remove(current);
                visited.add(current);
            }
        }
        return false;
    }

}
