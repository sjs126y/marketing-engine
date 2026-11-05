package com.sjs.marketing.domain.activity.service;

import com.sjs.marketing.domain.activity.model.entity.ActivityAccountEntity;
import com.sjs.marketing.domain.activity.model.entity.SkuRechargeEntity;

public interface IRaffleActivityAccountQuotaService {

    String createOrder(SkuRechargeEntity skuRechargeEntity);

    Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId);

    ActivityAccountEntity queryActivityAccountEntity(Long activityId, String userId);

    Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId);
}
