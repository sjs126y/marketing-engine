package com.sjs.marketing.domain.activity.service.quota;

import com.sjs.marketing.domain.activity.adapter.repository.IActivityRepository;
import com.sjs.marketing.domain.activity.model.aggregate.CreateQuotaOrderAggregate;
import com.sjs.marketing.domain.activity.model.entity.*;
import com.sjs.marketing.domain.activity.model.valobj.OrderStateVO;
import com.sjs.marketing.domain.activity.service.quota.rule.factory.DefaultActivityChainFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service
public class RaffleActivityAccountQuotaService extends AbstractRaffleActivityAccountQuota {

    public RaffleActivityAccountQuotaService(IActivityRepository repository, DefaultActivityChainFactory defaultActivityChainFactory) {
        super(repository, defaultActivityChainFactory);
    }

    @Override
    protected CreateQuotaOrderAggregate createOrderAggregate(SkuRechargeEntity skuRechargeEntity, ActivityEntity activityEntity, ActivitySkuEntity activitySkuEntity, ActivityCountEntity activityCountEntity) {
        ActivityOrderEntity activityOrderEntity = new ActivityOrderEntity();
        activityOrderEntity.setUserId(skuRechargeEntity.getUserId());
        activityOrderEntity.setSku(activitySkuEntity.getSku());
        activityOrderEntity.setActivityId(activityEntity.getActivityId());
        activityOrderEntity.setActivityName(activityEntity.getActivityName());
        activityOrderEntity.setStrategyId(activityEntity.getStrategyId());
        //生成订单id
        activityOrderEntity.setOrderId(RandomStringUtils.randomNumeric(12));
        activityOrderEntity.setOrderTime(new Date());
        activityOrderEntity.setTotalCount(activityCountEntity.getTotalCount());
        activityOrderEntity.setDayCount(activityCountEntity.getDayCount());
        activityOrderEntity.setMonthCount(activityCountEntity.getMonthCount());
        activityOrderEntity.setState(OrderStateVO.completed);
        activityOrderEntity.setOutBusinessNo(skuRechargeEntity.getOutBusinessNo());

        // 构建聚合对象
        return CreateQuotaOrderAggregate.builder()
                .userId(skuRechargeEntity.getUserId())
                .activityId(activitySkuEntity.getActivityId())
                .totalCount(activityCountEntity.getTotalCount())
                .dayCount(activityCountEntity.getDayCount())
                .monthCount(activityCountEntity.getMonthCount())
                .activityOrderEntity(activityOrderEntity)
                .skuConsumedAfter(activitySkuEntity.getConsumedAfter())
                .build();
    }

    @Override
    protected void doSaveOrder(CreateQuotaOrderAggregate createQuotaOrderAggregate) {
        repository.doSaveOrder(createQuotaOrderAggregate);
    }





    @Override
    public Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId) {
        return repository.queryRaffleActivityAccountDayPartakeCount(activityId, userId);
    }

    @Override
    public ActivityAccountEntity queryActivityAccountEntity(Long activityId, String userId) {
        return repository.queryActivityAccountEntity(userId, activityId);
    }

    @Override
    public Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId) {
        return repository.queryRaffleActivityAccountPartakeCount(activityId, userId);
    }
}
