package com.sjs.domain.activity.adapter.repository;

import com.sjs.domain.activity.model.aggregate.CreateOrderAggregate;
import com.sjs.domain.activity.model.entity.ActivityCountEntity;
import com.sjs.domain.activity.model.entity.ActivityEntity;
import com.sjs.domain.activity.model.entity.ActivitySkuEntity;

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
}
