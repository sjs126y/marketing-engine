package com.sjs.marketing.domain.activity.adapter.repository;

import com.sjs.marketing.domain.activity.model.aggregate.CreatePartakeOrderAggregate;
import com.sjs.marketing.domain.activity.model.aggregate.CreateQuotaOrderAggregate;
import com.sjs.marketing.domain.activity.model.entity.*;
import com.sjs.marketing.domain.activity.model.valobj.ActivitySkuStockSnapshotVO;

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

    Integer subtractionActivitySkuStock(Long sku, Date endDateTime);

    /**
     * 以绝对量回写 SKU 库存。
     *
     * <p>{@code consumedAfter} 是「扣减后的累计已扣数」，落库时算作
     * {@code 总量 - 已扣数}；只有比已记录的更大的值才会生效（单调保护），
     * 因此消息重复投递、乱序到达都不会把库存算错。</p>
     *
     * @param sku           SKU
     * @param consumedAfter 扣减后的累计已扣数
     */
    int updateAppliedConsumed(Long sku, Integer consumedAfter);

    /**
     * 全量 SKU 库存快照，供对账任务比对 Redis 与数据库。
     */
    List<ActivitySkuStockSnapshotVO> querySkuStockSnapshot();

    /**
     * 读取 Redis 中的 SKU 剩余库存，用于与数据库对账。
     *
     * @return 剩余库存；返回 {@code null} 表示该 SKU 还未预热（Redis 无此键）
     */
    Integer querySkuSurplusFromRedis(Long sku);
    
    ActivityAccountEntity queryActivityAccountByUserId(String userId, Long activityId);

    ActivityAccountMonthEntity queryActivityAccountMonthByUserId(String userId, Long activityId, String month);

    ActivityAccountDayEntity queryActivityAccountDayByUserId(String userId, Long activityId, String day);

    UserRaffleOrderEntity queryNoUsedRaffleOrder(PartakeRaffleActivityEntity partakeRaffleActivityEntity);

    void saveCreatePartakeOrderAggregate(CreatePartakeOrderAggregate createPartakeOrderAggregate);

    List<ActivitySkuEntity> queryActivitySkuListByActivityId(Long activityId);

    Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId);

    ActivityAccountEntity queryActivityAccountEntity(String userId, Long activityId);

    Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId);
}
