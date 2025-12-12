package com.sjs.domain.activity.adapter.repository;

import com.sjs.domain.activity.model.aggregate.CreateOrderAggregate;
import com.sjs.domain.activity.model.entity.ActivityCountEntity;
import com.sjs.domain.activity.model.entity.ActivityEntity;
import com.sjs.domain.activity.model.entity.ActivitySkuEntity;
import com.sjs.domain.activity.model.valobj.ActivitySkuStockKeyVO;

import java.util.Date;

public interface IActivityRepository {

    /**
     * 查询活动sku
     * @param sku
     * @return
     */
    ActivitySkuEntity queryActivitySku(Long sku);

    /**
     * 查询活动次数
     * @param activityCountId
     * @return
     */
    ActivityCountEntity queryActivityCountByActivityCountId(Long activityCountId);

    /**
     * 查询活动
     * @param activityId
     * @return
     */
    ActivityEntity queryActivityByActivityId(Long activityId);

    /**
     * 保存订单
     * @param createOrderAggregate
     */
    void doSaveOrder(CreateOrderAggregate createOrderAggregate);

    void cacheActivitySkuStockCount(String cacheKey, Integer stockCount);

    boolean subtractionActivitySkuStock(Long sku, Date endDateTime);

    void activitySkuStockConsumeSendQueue(ActivitySkuStockKeyVO activitySkuStockKeyVO);

    ActivitySkuStockKeyVO takeQueueValue();

    void clearQueue();

    void updateActivitySkuStockCount(Long sku);

    void clearActivitySkuStockCount(Long sku);
}
