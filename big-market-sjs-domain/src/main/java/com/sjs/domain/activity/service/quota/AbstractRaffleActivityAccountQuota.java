package com.sjs.domain.activity.service.quota;

import com.sjs.domain.activity.adapter.repository.IActivityRepository;
import com.sjs.domain.activity.model.aggregate.CreateQuotaOrderAggregate;
import com.sjs.domain.activity.model.entity.*;
import com.sjs.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.sjs.domain.activity.service.quota.rule.IActionChain;
import com.sjs.domain.activity.service.quota.rule.factory.DefaultActivityChainFactory;
import com.sjs.types.enums.ResponseCode;
import com.sjs.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public abstract class AbstractRaffleActivityAccountQuota extends RaffleActivityAccountQuotaSupport implements IRaffleActivityAccountQuotaService {


    public AbstractRaffleActivityAccountQuota(IActivityRepository repository, DefaultActivityChainFactory defaultActivityChainFactory) {
        super(repository, defaultActivityChainFactory);
    }

    @Override
    public String createOrder(SkuRechargeEntity skuRechargeEntity) {
        log.info("创建SKU充值订单, skuRechargeEntity : {}", skuRechargeEntity);

        //1.参数校验
        String userId = skuRechargeEntity.getUserId();
        Long sku = skuRechargeEntity.getSku();
        String outBusinessNo = skuRechargeEntity.getOutBusinessNo();

        if (null == sku || StringUtils.isBlank(userId) || StringUtils.isBlank(outBusinessNo)) {
            log.error("参数错误");
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }

        //2.查询基本信息
        ActivitySkuEntity activitySkuEntity = queryActivitySku(sku);
        ActivityEntity activityEntity = queryRaffleActivityByActivityId(activitySkuEntity.getActivityId());
        ActivityCountEntity activityCountEntity = queryRaffleActivityCountByActivityCountId(activitySkuEntity.getActivityCountId());

        //3.进入责任链工厂，进行规则校验
        IActionChain iActionChain = defaultActivityChainFactory.openActionChain();
        boolean status = iActionChain.action(activitySkuEntity, activityEntity, activityCountEntity);
        if(!status){
            log.error("活动规则校验失败");
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }

        //4.构建聚合对象
        CreateQuotaOrderAggregate createQuotaOrderAggregate = createOrderAggregate(skuRechargeEntity, activityEntity, activitySkuEntity, activityCountEntity);

        //5.保存订单
        doSaveOrder(createQuotaOrderAggregate);

        return createQuotaOrderAggregate.getActivityOrderEntity().getOrderId();
    }

    protected abstract CreateQuotaOrderAggregate createOrderAggregate(SkuRechargeEntity skuRechargeEntity, ActivityEntity activityEntity, ActivitySkuEntity activitySkuEntity, ActivityCountEntity activityCountEntity);

    protected abstract void doSaveOrder(CreateQuotaOrderAggregate createQuotaOrderAggregate);
}
