package com.sjs.domain.activity.service;

import com.sjs.domain.activity.model.entity.ActivityAccountEntity;
import com.sjs.domain.activity.model.entity.SkuRechargeEntity;

public interface IRaffleActivityAccountQuotaService {

    String createOrder(SkuRechargeEntity skuRechargeEntity);

    Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId);

    ActivityAccountEntity queryActivityAccountEntity(Long activityId, String userId);

    Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId);
}
