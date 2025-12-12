package com.sjs.domain.activity.service.rule.impl;

import com.sjs.domain.activity.model.entity.ActivityCountEntity;
import com.sjs.domain.activity.model.entity.ActivityEntity;
import com.sjs.domain.activity.model.entity.ActivitySkuEntity;
import com.sjs.domain.activity.service.rule.AbstractActionChain;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Slf4j
@Component("activity_sku_stock_action")
public class ActivitySkuStockActionChain extends AbstractActionChain {
    @Override
    public boolean action(ActivitySkuEntity activitySkuEntity, ActivityEntity activityEntity, ActivityCountEntity activityCountEntity) {
        log.info("活动sku库存校验");

        return true;
    }
}
