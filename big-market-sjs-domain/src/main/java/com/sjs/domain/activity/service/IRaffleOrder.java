package com.sjs.domain.activity.service;

import com.sjs.domain.activity.model.entity.ActivityOrderEntity;
import com.sjs.domain.activity.model.entity.ActivityShopCartEntity;
import com.sjs.domain.activity.model.entity.ActivitySkuEntity;

public interface IRaffleOrder {
    /**
     * 创建抽奖活动单
     * @param activityShopCartEntity
     * @return
     */
    ActivityOrderEntity createRaffleActivityOrder(ActivityShopCartEntity activityShopCartEntity);

}
