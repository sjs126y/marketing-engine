package com.sjs.domain.rebate.service;

import com.sjs.domain.rebate.model.entity.BehaviorEntity;

import java.util.List;

public interface IBehaviorRebateService {

    List<String> createOrder(BehaviorEntity behaviorEntity);

}
