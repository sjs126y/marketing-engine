package com.sjs.marketing.domain.activity.service.quota.rule.impl;

import com.sjs.marketing.domain.activity.model.entity.ActivityCountEntity;
import com.sjs.marketing.domain.activity.model.entity.ActivityEntity;
import com.sjs.marketing.domain.activity.model.entity.ActivitySkuEntity;
import com.sjs.marketing.domain.activity.service.armory.IActivityDispatch;
import com.sjs.marketing.domain.activity.service.quota.rule.AbstractActionChain;
import com.sjs.marketing.types.enums.ResponseCode;
import com.sjs.marketing.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 活动责任链 - SKU 库存扣减。
 *
 * <p>只做 Redis 原子扣减并记录「扣减后的累计已扣数」，
 * 真正的数据库回写由本地事务里的 outbox 消息异步完成（绝对量 + 单调保护）。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Component("activity_sku_stock_action")
public class ActivitySkuStockActionChain extends AbstractActionChain {

    @Resource
    private IActivityDispatch activityDispatch;

    @Override
    public boolean action(ActivitySkuEntity activitySkuEntity, ActivityEntity activityEntity, ActivityCountEntity activityCountEntity) {
        Long sku = activitySkuEntity.getSku();
        log.info("活动责任链-商品库存处理【有效期、状态、库存(sku)】开始。sku:{} activityId:{}", sku, activityEntity.getActivityId());

        Integer surplus = activityDispatch.subtractionActivitySkuStock(sku, activityEntity.getEndDateTime());
        if (surplus == null) {
            throw new AppException(ResponseCode.ACTIVITY_SKU_STOCK_ERROR.getCode(), ResponseCode.ACTIVITY_SKU_STOCK_ERROR.getInfo());
        }

        // 把扣减后的累计已扣数留在实体上，供事务内写 outbox 时使用
        int total = activitySkuEntity.getStockCount() == null ? 0 : activitySkuEntity.getStockCount();
        activitySkuEntity.setConsumedAfter(total - surplus);
        activitySkuEntity.setStockCountSurplus(surplus);

        log.info("活动责任链-商品库存处理成功。sku:{} surplus:{} consumedAfter:{}",
                sku, surplus, activitySkuEntity.getConsumedAfter());
        return true;
    }
}
