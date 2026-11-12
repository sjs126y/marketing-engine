package com.sjs.marketing.domain.activity.service;

import com.sjs.marketing.domain.activity.model.valobj.ActivitySkuStockReconcileVO;
import com.sjs.marketing.domain.activity.model.valobj.ActivitySkuStockSnapshotVO;

import java.util.List;

/**
 * 活动 SKU 库存回写与对账。
 *
 * @author marketing-engine contributors
 */
public interface IActivityStockService {

    /**
     * 以绝对量回写库存（幂等，重复消费与乱序到达都安全）。
     *
     * @param sku           SKU
     * @param consumedAfter 扣减后的累计已扣数
     */
    void applyConsumed(Long sku, Integer consumedAfter);

    /**
     * 全量库存快照，供对账使用。
     */
    List<ActivitySkuStockSnapshotVO> queryStockSnapshot();

    /**
     * 对比 Redis 与数据库的已扣数，返回每个 SKU 的对账结果。
     */
    List<ActivitySkuStockReconcileVO> reconcile();

}
