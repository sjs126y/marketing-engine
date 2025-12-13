package com.sjs.domain.activity.service.quota;

import com.sjs.domain.activity.adapter.repository.IActivityRepository;
import com.sjs.domain.activity.model.entity.ActivityCountEntity;
import com.sjs.domain.activity.model.entity.ActivityEntity;
import com.sjs.domain.activity.model.entity.ActivitySkuEntity;
import com.sjs.domain.activity.service.quota.rule.factory.DefaultActivityChainFactory;

public class RaffleActivityAccountQuotaSupport {

    protected IActivityRepository repository;

    protected DefaultActivityChainFactory defaultActivityChainFactory;

    public RaffleActivityAccountQuotaSupport(IActivityRepository repository, DefaultActivityChainFactory defaultActivityChainFactory) {
        this.repository = repository;
        this.defaultActivityChainFactory = defaultActivityChainFactory;
    }

    public ActivitySkuEntity queryActivitySku(Long sku) {
        return repository.queryActivitySku(sku);
    }

    public ActivityEntity queryRaffleActivityByActivityId(Long activityId) {
        return repository.queryActivityByActivityId(activityId);
    }

    public ActivityCountEntity queryRaffleActivityCountByActivityCountId(Long activityCountId) {
        return repository.queryActivityCountByActivityCountId(activityCountId);
    }
}
