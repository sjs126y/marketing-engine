package com.sjs.marketing.domain.strategy.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author marketing-engine contributors
 * @description 策略奖品库存Key标识值对象
 * @create 2024-02-09 11:24
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StrategyAwardStockKeyVO {

    // 策略ID
    private Long strategyId;
    // 奖品ID
    private Integer awardId;
    /**
     * 扣减后的累计已扣数（Redis 口径）。
     *
     * <p>落库时按「总量 - 已扣数」绝对量回写，重复消费与乱序重放都幂等——
     * 原先按「-1」增量写，队列堆积后重放会把库存扣成负数。</p>
     */
    private Integer consumedAfter;

}
