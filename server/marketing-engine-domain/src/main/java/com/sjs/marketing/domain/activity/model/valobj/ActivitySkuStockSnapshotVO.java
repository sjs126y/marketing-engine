package com.sjs.marketing.domain.activity.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SKU 库存快照，用于 Redis 与数据库之间的对账。
 *
 * @author marketing-engine contributors
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActivitySkuStockSnapshotVO {

    /** SKU */
    private Long sku;
    /** 库存总量 */
    private Integer stockCount;
    /** 数据库中的剩余库存 */
    private Integer stockCountSurplus;
    /** 数据库中去重后记录的累计已扣数 */
    private Integer appliedConsumed;

    /**
     * 数据库口径下的已扣数。
     */
    public int consumedByDB() {
        int total = stockCount == null ? 0 : stockCount;
        int surplus = stockCountSurplus == null ? 0 : stockCountSurplus;
        return total - surplus;
    }

}
