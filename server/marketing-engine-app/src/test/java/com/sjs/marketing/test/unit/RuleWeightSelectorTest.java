package com.sjs.marketing.test.unit;

import com.sjs.marketing.domain.strategy.service.rule.chain.RuleWeightSelector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权重档位选择的边界验证。
 *
 * <p>规则值样例：{@code "10:102,103 70:106,107 1000:104,105"}，
 * 语义是「取满足 userScore >= 阈值 的最小档位」。</p>
 *
 * @author marketing-engine contributors
 */
class RuleWeightSelectorTest {

    private final Map<Long, String> groups = buildGroups();

    @Test
    @DisplayName("未达到最低门槛时不命中，交回责任链下一节点")
    void shouldNotHitWhenBelowFirstThreshold() {
        assertThat(RuleWeightSelector.select(groups, 0)).isNull();
        assertThat(RuleWeightSelector.select(groups, 9)).isNull();
    }

    @Test
    @DisplayName("恰好等于门槛时命中该档位（边界包含）")
    void shouldHitExactlyAtThreshold() {
        assertThat(RuleWeightSelector.select(groups, 10)).isEqualTo("10:102,103");
        assertThat(RuleWeightSelector.select(groups, 70)).isEqualTo("70:106,107");
        assertThat(RuleWeightSelector.select(groups, 1000)).isEqualTo("1000:104,105");
    }

    @Test
    @DisplayName("跨过门槛但未到下一档时，仍停留在当前档位")
    void shouldStayInCurrentTierBetweenThresholds() {
        assertThat(RuleWeightSelector.select(groups, 11)).isEqualTo("10:102,103");
        assertThat(RuleWeightSelector.select(groups, 69)).isEqualTo("10:102,103");
        assertThat(RuleWeightSelector.select(groups, 999)).isEqualTo("70:106,107");
    }

    @Test
    @DisplayName("超过最高门槛时命中最高档位，不会溢出")
    void shouldClampToHighestTier() {
        assertThat(RuleWeightSelector.select(groups, 1000)).isEqualTo("1000:104,105");
        assertThat(RuleWeightSelector.select(groups, 999999)).isEqualTo("1000:104,105");
    }

    @Test
    @DisplayName("规则未配置或为空时返回 null，不抛异常")
    void shouldReturnNullForEmptyConfig() {
        assertThat(RuleWeightSelector.select(null, 100)).isNull();
        assertThat(RuleWeightSelector.select(Map.of(), 100)).isNull();
    }

    @Test
    @DisplayName("阈值乱序传入也能正确选择（内部会排序）")
    void shouldSortThresholdsInternally() {
        Map<Long, String> unordered = new LinkedHashMap<>();
        unordered.put(1000L, "1000:104,105");
        unordered.put(10L, "10:102,103");
        unordered.put(70L, "70:106,107");

        assertThat(RuleWeightSelector.select(unordered, 80)).isEqualTo("70:106,107");
        assertThat(RuleWeightSelector.select(unordered, 5)).isNull();
    }

    private Map<Long, String> buildGroups() {
        Map<Long, String> map = new LinkedHashMap<>();
        map.put(10L, "10:102,103");
        map.put(70L, "70:106,107");
        map.put(1000L, "1000:104,105");
        return map;
    }

}
