package com.sjs.domain.activity.service.armory;

import com.sjs.domain.activity.adapter.repository.IActivityRepository;
import com.sjs.domain.activity.model.entity.ActivitySkuEntity;
import com.sjs.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class ActivityArmory implements IActivityArmory, IActivityDispatch{

    @Resource
    private IActivityRepository repository;

    @Override
    public boolean assembleActivity(Long activityId) {
        //获得sku
        List<ActivitySkuEntity> activitySkuEntities = repository.queryActivitySkuListByActivityId(activityId);
        for (ActivitySkuEntity activitySkuEntity : activitySkuEntities) {
            cacheActivitySkuStockCount(activitySkuEntity.getSku(), activitySkuEntity.getStockCount());

            repository.queryActivityCountByActivityCountId(activitySkuEntity.getActivityCountId());
        }

        repository.queryActivityByActivityId(activityId);

        return true;
    }

    @Override
    public boolean assembleActivitySku(Long sku) {
        //给sku库存预热
        ActivitySkuEntity activitySkuEntity = repository.queryActivitySku(sku);
        cacheActivitySkuStockCount(sku, activitySkuEntity.getStockCount());

        //给次数库预热
        repository.queryActivityCountByActivityCountId(activitySkuEntity.getActivityCountId());
        //给sku活动预热
        repository.queryActivityByActivityId(activitySkuEntity.getActivityId());

        return true;
    }

    private void cacheActivitySkuStockCount(Long sku, Integer stockCount) {
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_STOCK_COUNT_KEY + sku;
        repository.cacheActivitySkuStockCount(cacheKey, stockCount);
    }

    @Override
    public boolean subtractionActivitySkuStock(Long sku, Date endDateTime) {
        return repository.subtractionActivitySkuStock(sku, endDateTime);
    }
}
