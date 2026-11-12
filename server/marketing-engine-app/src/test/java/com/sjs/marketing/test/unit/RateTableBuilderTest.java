package com.sjs.marketing.test.unit;

import com.sjs.marketing.domain.strategy.model.entity.StrategyAwardEntity;
import com.sjs.marketing.domain.strategy.service.armory.StrategyArmory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 概率表装配的正确性验证。
 *
 * <p>这些用例不依赖 Spring、数据库或 Redis：{@code buildRateTable} 是纯函数，
 * 输入奖品概率、输出下标查找表，因此可以在 CI 里裸跑。</p>
 *
 * <p>校验思路：把生成的表按奖品统计占位数量，再还原成实际概率，
 * 与配置概率比较。期望值不是「完全相等」而是「落在容差内」，
 * 因为装配过程按最小概率向上取整，本身会有极小误差。</p>
 *
 * @author marketing-engine contributors
 */
class RateTableBuilderTest {

    @Test
    @DisplayName("概率归一时，实际占位比例接近配置（向上取整会让总槽位略多于区间长度）")
    void shouldMatchConfiguredRateWhenNormalized() {
        // 0.5 / 0.3 / 0.2，和正好为 1
        List<StrategyAwardEntity> awards = List.of(
                award(101, "0.5"),
                award(102, "0.3"),
                award(103, "0.2"));

        Map<Integer, Integer> table = StrategyArmory.buildRateTable(new ArrayList<>(awards));

        // 最小概率 0.2 → 区间长度 = 1 / 0.2 = 5
        // 各奖品槽位：ceil(5×0.5)=3、ceil(5×0.3)=2、ceil(5×0.2)=1，合计 6
        // 这是算法固有的特性：向上取整保证每个奖品至少 1 个槽位，总量可能超出区间长度
        assertThat(table).hasSize(6);

        Map<Integer, Integer> counts = countByAward(table);
        assertThat(counts.get(101)).isEqualTo(3);
        assertThat(counts.get(102)).isEqualTo(2);
        assertThat(counts.get(103)).isEqualTo(1);

        // 实际概率 3/6、2/6、1/6，与配置 0.5/0.3/0.2 的误差在取整范围内
        assertThat(counts.get(101) / 6.0).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.05));
        assertThat(counts.get(102) / 6.0).isCloseTo(0.3, org.assertj.core.data.Offset.offset(0.05));
        assertThat(counts.get(103) / 6.0).isCloseTo(0.2, org.assertj.core.data.Offset.offset(0.05));
    }

    @Test
    @DisplayName("表的下标连续且覆盖整个区间，不出现空洞")
    void shouldProduceContiguousIndexes() {
        List<StrategyAwardEntity> awards = List.of(
                award(201, "0.25"),
                award(202, "0.25"),
                award(203, "0.25"),
                award(204, "0.25"));

        Map<Integer, Integer> table = StrategyArmory.buildRateTable(new ArrayList<>(awards));

        assertThat(table).hasSize(4);
        for (int i = 0; i < table.size(); i++) {
            assertThat(table).containsKey(i);
            assertThat(table.get(i)).isNotNull();
        }
    }

    @Test
    @DisplayName("概率不均等时，高概率奖品的占位严格多于低概率奖品")
    void shouldGiveMoreSlotsToHigherRate() {
        List<StrategyAwardEntity> awards = List.of(
                award(301, "0.0001"),
                award(302, "0.4999"),
                award(303, "0.5"));

        Map<Integer, Integer> table = StrategyArmory.buildRateTable(new ArrayList<>(awards));
        Map<Integer, Integer> counts = countByAward(table);

        assertThat(counts.get(303)).isGreaterThan(counts.get(302));
        assertThat(counts.get(302)).isGreaterThan(counts.get(301));
    }

    @Test
    @DisplayName("权重档位子集独立装配：档位内奖品概率之和为 1 时各占一半")
    void shouldNormalizeWithinWeightSubset() {
        // 档位只含 102、103，各 0.5
        List<StrategyAwardEntity> awards = List.of(
                award(102, "0.5"),
                award(103, "0.5"));

        Map<Integer, Integer> table = StrategyArmory.buildRateTable(new ArrayList<>(awards));
        Map<Integer, Integer> counts = countByAward(table);

        assertThat(table).hasSize(2);
        assertThat(counts.get(102)).isEqualTo(1);
        assertThat(counts.get(103)).isEqualTo(1);
    }

    private StrategyAwardEntity award(int awardId, String rate) {
        StrategyAwardEntity entity = new StrategyAwardEntity();
        entity.setStrategyId(100006L);
        entity.setAwardId(awardId);
        entity.setAwardRate(new BigDecimal(rate));
        entity.setAwardCount(100);
        return entity;
    }

    private Map<Integer, Integer> countByAward(Map<Integer, Integer> table) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Integer awardId : table.values()) {
            counts.merge(awardId, 1, Integer::sum);
        }
        return counts;
    }

}
