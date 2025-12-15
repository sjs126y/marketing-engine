package com.sjs.domain.rebate.adapter.repository;

import com.sjs.domain.rebate.model.aggregate.BehaviorRebateAggregate;
import com.sjs.domain.rebate.model.entity.BehaviorRebateOrderEntity;
import com.sjs.domain.rebate.model.valobj.BehaviorTypeVO;
import com.sjs.domain.rebate.model.valobj.DailyBehaviorRebateVO;

import java.util.List;

public interface IBehaviorRepository {

    List<DailyBehaviorRebateVO> queryDailyBehaviorRebateConfig(BehaviorTypeVO behaviorTypeVO);

    void saveUserRebateRecord(String userId, List<BehaviorRebateAggregate> behaviorRebateAggregates);

    List<BehaviorRebateOrderEntity> queryOrderByOutBusinessNo(String userId, String outBusinessNo);
}
