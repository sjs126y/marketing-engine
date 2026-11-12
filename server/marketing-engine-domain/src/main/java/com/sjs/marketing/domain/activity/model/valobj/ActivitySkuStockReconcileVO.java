package com.sjs.marketing.domain.activity.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一次 SKU 库存对账的结果。
 *
 * <p>对账的口径是「已扣数」：Redis 侧按 {@code 总量 - Redis 剩余}，
 * 数据库侧按 {@code 总量 - 数据库剩余}，两者不一致说明有消息丢了或重复应用。</p>
 *
 * @author marketing-engine contributors
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActivitySkuStockReconcileVO {

    /** SKU */
    private Long sku;
    /** 库存总量 */
    private Integer stockCount;
    /** Redis 口径的已扣数 */
    private Integer redisConsumed;
    /** 数据库口径的已扣数 */
    private Integer dbConsumed;
    /** 是否跳过（该 SKU 未预热，Redis 无此键） */
    private boolean skipped;

    /**
     * 偏差：数据库已扣数 - Redis 已扣数。
     * 正数表示数据库扣多了，负数表示数据库还没追上 Redis（少卖）。
     */
    public int diff() {
        return dbConsumed - redisConsumed;
    }

    public boolean inconsistent(int threshold) {
        return !skipped && Math.abs(diff()) > threshold;
    }

}
