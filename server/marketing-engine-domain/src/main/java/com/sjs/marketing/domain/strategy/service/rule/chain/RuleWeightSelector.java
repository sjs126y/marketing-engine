package com.sjs.marketing.domain.strategy.service.rule.chain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 权重档位选择。
 *
 * <p>规则值形如 {@code "10:102,103 70:106,107 1000:104,105"}，
 * 键是「用户累计消耗次数」阈值，值是该档位可抽的奖品列表
 * （同时也是预热阶段生成的子表 key）。</p>
 *
 * <p>选择语义：取**满足 {@code userScore >= 阈值} 的最小档位**。
 * 也就是刚跨过门槛就进对应档位，未达任何门槛则不命中、交给下一责任链节点。</p>
 *
 * @author marketing-engine contributors
 */
public final class RuleWeightSelector {

    private RuleWeightSelector() {
    }

    /**
     * 选出命中的档位。
     *
     * @param groups    阈值 → 档位 token（完整 rule_value 片段）
     * @param userScore 用户在该策略下的累计消耗次数
     * @return 命中的档位 token；未命中返回 {@code null}
     */
    public static String select(Map<Long, String> groups, int userScore) {
        if (groups == null || groups.isEmpty()) {
            return null;
        }
        List<Long> sorted = new ArrayList<>(groups.keySet());
        Collections.sort(sorted);

        Long hit = null;
        for (Long threshold : sorted) {
            if (userScore >= threshold) {
                hit = threshold;
            } else {
                break;
            }
        }
        return hit == null ? null : groups.get(hit);
    }

}
