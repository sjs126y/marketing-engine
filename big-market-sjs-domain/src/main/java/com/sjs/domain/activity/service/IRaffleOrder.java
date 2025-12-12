package com.sjs.domain.activity.service;

import com.sjs.domain.activity.model.entity.ActivityOrderEntity;
import com.sjs.domain.activity.model.entity.ActivityShopCartEntity;
import com.sjs.domain.activity.model.entity.ActivitySkuEntity;
import com.sjs.domain.activity.model.entity.SkuRechargeEntity;

public interface IRaffleOrder {

    String createSkuRechargeOrder(SkuRechargeEntity skuRechargeEntity);

}
