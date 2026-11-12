package com.sjs.marketing.domain.activity.service.armory;

import java.util.Date;

/**
 * 活动库存扣减能力。
 *
 * @author marketing-engine contributors
 */
public interface IActivityDispatch {

    /**
     * Redis 原子扣减活动 SKU 库存。
     *
     * @param sku         SKU
     * @param endDateTime 活动结束时间，用于兜底锁过期时间
     * @return 扣减后的剩余库存；返回 {@code null} 表示库存不足（含并发抢占失败）
     */
    Integer subtractionActivitySkuStock(Long sku, Date endDateTime);

}
