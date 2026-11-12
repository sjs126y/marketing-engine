package com.sjs.marketing.infrastructure.adapter.repository;

import com.sjs.marketing.infrastructure.outbox.OutboxService;
import com.sjs.marketing.types.util.JsonUtils;
import com.sjs.marketing.domain.rebate.adapter.repository.IBehaviorRepository;
import com.sjs.marketing.domain.rebate.model.aggregate.BehaviorRebateAggregate;
import com.sjs.marketing.domain.rebate.model.entity.BehaviorRebateOrderEntity;
import com.sjs.marketing.domain.rebate.model.entity.TaskEntity;
import com.sjs.marketing.domain.rebate.model.valobj.BehaviorTypeVO;
import com.sjs.marketing.domain.rebate.model.valobj.DailyBehaviorRebateVO;
import com.sjs.marketing.infrastructure.dao.IDailyBehaviorRebateDao;
import com.sjs.marketing.infrastructure.dao.IUserBehaviorRebateOrderDao;
import com.sjs.marketing.infrastructure.dao.po.DailyBehaviorRebate;
import com.sjs.marketing.infrastructure.dao.po.UserBehaviorRebateOrder;
import com.sjs.marketing.types.enums.ResponseCode;
import com.sjs.marketing.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Repository
public class BehaviorRepository implements IBehaviorRepository {

    @Resource
    private IUserBehaviorRebateOrderDao userBehaviorRebateOrderDao;
    @Resource
    private IDailyBehaviorRebateDao dailyBehaviorRebateDao;
    @Resource
    private OutboxService outboxService;
    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public List<DailyBehaviorRebateVO> queryDailyBehaviorRebateConfig(BehaviorTypeVO behaviorTypeVO) {
        List<DailyBehaviorRebate> dailyBehaviorRebates = dailyBehaviorRebateDao.queryDailyBehaviorRebateByBehaviorType(behaviorTypeVO.getCode());
        List<DailyBehaviorRebateVO> dailyBehaviorRebateVOS = new ArrayList<>(dailyBehaviorRebates.size());
        for (DailyBehaviorRebate dailyBehaviorRebate : dailyBehaviorRebates) {
            dailyBehaviorRebateVOS.add(DailyBehaviorRebateVO.builder()
                    .behaviorType(dailyBehaviorRebate.getBehaviorType())
                    .rebateDesc(dailyBehaviorRebate.getRebateDesc())
                    .rebateType(dailyBehaviorRebate.getRebateType())
                    .rebateConfig(dailyBehaviorRebate.getRebateConfig())
                    .build());
        }
        return dailyBehaviorRebateVOS;
    }

    @Override
    public void saveUserRebateRecord(String userId, List<BehaviorRebateAggregate> behaviorRebateAggregates) {
        // 分库分表由 ShardingSphere 依据 SQL 中的 user_id 自动路由
        transactionTemplate.execute(status -> {
            try {
                for (BehaviorRebateAggregate behaviorRebateAggregate : behaviorRebateAggregates) {
                    BehaviorRebateOrderEntity behaviorRebateOrderEntity = behaviorRebateAggregate.getBehaviorRebateOrderEntity();
                    TaskEntity taskEntity = behaviorRebateAggregate.getTaskEntity();

                    //先插入流水订单
                    UserBehaviorRebateOrder userBehaviorRebateOrder = new UserBehaviorRebateOrder();
                    userBehaviorRebateOrder.setUserId(behaviorRebateOrderEntity.getUserId());
                    userBehaviorRebateOrder.setOrderId(behaviorRebateOrderEntity.getOrderId());
                    userBehaviorRebateOrder.setBehaviorType(behaviorRebateOrderEntity.getBehaviorType());
                    userBehaviorRebateOrder.setRebateDesc(behaviorRebateOrderEntity.getRebateDesc());
                    userBehaviorRebateOrder.setRebateType(behaviorRebateOrderEntity.getRebateType());
                    userBehaviorRebateOrder.setRebateConfig(behaviorRebateOrderEntity.getRebateConfig());
                    userBehaviorRebateOrder.setBizId(behaviorRebateOrderEntity.getBizId());
                    userBehaviorRebateOrder.setOutBusinessNo(behaviorRebateOrderEntity.getOutBusinessNo());
                    userBehaviorRebateOrderDao.insert(userBehaviorRebateOrder);

                    // 发奖/返利消息写入 outbox（与流水同事务，事务外由调度器投递）
                    outboxService.append(taskEntity.getUserId(), taskEntity.getTopic(),
                            taskEntity.getMessageId(), JsonUtils.toJson(taskEntity.getMessage()));
                }

                return 1;
            } catch (DuplicateKeyException e) {
                throw new AppException(ResponseCode.INDEX_DUP.getCode(), ResponseCode.INDEX_DUP.getInfo());
            } catch (Exception e) {
                log.error("写入返利记录失败 e:{} ", e);
                status.setRollbackOnly();
                throw new AppException(ResponseCode.UN_ERROR.getCode(), ResponseCode.UN_ERROR.getInfo(), e);
            }
        });
    }

    @Override
    public List<BehaviorRebateOrderEntity> queryOrderByOutBusinessNo(String userId, String outBusinessNo) {
        UserBehaviorRebateOrder userBehaviorRebateOrderReq = new UserBehaviorRebateOrder();
        userBehaviorRebateOrderReq.setUserId(userId);
        userBehaviorRebateOrderReq.setOutBusinessNo(outBusinessNo);

        List<UserBehaviorRebateOrder> userBehaviorRebateOrderResList = userBehaviorRebateOrderDao.queryOrderByOutBusinessNo(userBehaviorRebateOrderReq);
        List<BehaviorRebateOrderEntity> behaviorRebateOrderEntities = new ArrayList<>(userBehaviorRebateOrderResList.size());
        for (UserBehaviorRebateOrder userBehaviorRebateOrder : userBehaviorRebateOrderResList) {
            BehaviorRebateOrderEntity behaviorRebateOrderEntity = BehaviorRebateOrderEntity.builder()
                    .userId(userBehaviorRebateOrder.getUserId())
                    .orderId(userBehaviorRebateOrder.getOrderId())
                    .behaviorType(userBehaviorRebateOrder.getBehaviorType())
                    .rebateDesc(userBehaviorRebateOrder.getRebateDesc())
                    .rebateType(userBehaviorRebateOrder.getRebateType())
                    .rebateConfig(userBehaviorRebateOrder.getRebateConfig())
                    .outBusinessNo(userBehaviorRebateOrder.getOutBusinessNo())
                    .bizId(userBehaviorRebateOrder.getBizId())
                    .build();
            behaviorRebateOrderEntities.add(behaviorRebateOrderEntity);
        }

        return behaviorRebateOrderEntities;
    }
}
