package com.sjs.domain.activity.service;

import com.sjs.domain.activity.adapter.repository.IActivityRepository;
import com.sjs.domain.activity.model.entity.*;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;

@Slf4j
public abstract class AbstractRaffleActivity implements IRaffleOrder{

    protected IActivityRepository repository;

    public AbstractRaffleActivity(IActivityRepository repository) {
        this.repository = repository;
    }

    @Override
    public ActivityOrderEntity createRaffleActivityOrder(ActivityShopCartEntity activityShopCartEntity) {
        //1.先查询sku
        ActivitySkuEntity activitySkuEntity = repository.queryActivitySku(activityShopCartEntity.getSku());
        //2.在查询活动
        ActivityEntity activityEntity = repository.queryActivityByActivityId(activitySkuEntity.getActivityId());
        //3.再查询活动次数
        ActivityCountEntity activityCountEntity = repository.queryActivityCountByActivityCountId(activitySkuEntity.getActivityCountId());

        //返回数据
        return ActivityOrderEntity.builder().build();
    }
}
