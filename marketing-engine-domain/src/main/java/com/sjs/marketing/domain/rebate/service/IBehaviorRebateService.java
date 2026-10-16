package com.sjs.marketing.domain.rebate.service;

import com.sjs.marketing.domain.rebate.model.entity.BehaviorEntity;
import com.sjs.marketing.domain.rebate.model.entity.BehaviorRebateOrderEntity;

import java.util.List;

public interface IBehaviorRebateService {

    List<String> createOrder(BehaviorEntity behaviorEntity);

    List<BehaviorRebateOrderEntity> queryOrderByOutBusinessNo(String userId, String outBusinessNo);
}
