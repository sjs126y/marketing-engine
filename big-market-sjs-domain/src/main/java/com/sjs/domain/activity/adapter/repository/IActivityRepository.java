package com.sjs.domain.activity.adapter.repository;

import com.sjs.domain.activity.model.aggregate.CreatePartakeOrderAggregate;
import com.sjs.domain.activity.model.aggregate.CreateQuotaOrderAggregate;
import com.sjs.domain.activity.model.entity.*;
import com.sjs.domain.activity.model.valobj.ActivitySkuStockKeyVO;

import java.util.Date;
import java.util.List;

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
     * @param createQuotaOrderAggregate
     */
    void doSaveOrder(CreateQuotaOrderAggregate createQuotaOrderAggregate);

    void cacheActivitySkuStockCount(String cacheKey, Integer stockCount);

    boolean subtractionActivitySkuStock(Long sku, Date endDateTime);

    void activitySkuStockConsumeSendQueue(ActivitySkuStockKeyVO activitySkuStockKeyVO);

    ActivitySkuStockKeyVO takeQueueValue();

    void clearQueue();

    void updateActivitySkuStockCount(Long sku);

    void clearActivitySkuStockCount(Long sku);

    ActivityAccountEntity queryActivityAccountByUserId(String userId, Long activityId);

    ActivityAccountMonthEntity queryActivityAccountMonthByUserId(String userId, Long activityId, String month);

    ActivityAccountDayEntity queryActivityAccountDayByUserId(String userId, Long activityId, String day);

    UserRaffleOrderEntity queryNoUsedRaffleOrder(PartakeRaffleActivityEntity partakeRaffleActivityEntity);

    void saveCreatePartakeOrderAggregate(CreatePartakeOrderAggregate createPartakeOrderAggregate);

    List<ActivitySkuEntity> queryActivitySkuListByActivityId(Long activityId);
}
