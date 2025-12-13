package com.sjs.domain.activity.service.quota.rule;

import com.sjs.domain.activity.model.entity.ActivityCountEntity;
import com.sjs.domain.activity.model.entity.ActivityEntity;
import com.sjs.domain.activity.model.entity.ActivitySkuEntity;

public interface IActionChain extends IActionChainArmory{

    boolean action(ActivitySkuEntity activitySkuEntity, ActivityEntity activityEntity, ActivityCountEntity activityCountEntity);

}
