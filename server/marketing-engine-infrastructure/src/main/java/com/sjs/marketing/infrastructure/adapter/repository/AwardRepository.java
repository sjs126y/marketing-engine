package com.sjs.marketing.infrastructure.adapter.repository;

import com.sjs.marketing.domain.award.adapter.repository.IAwardRepository;
import com.sjs.marketing.domain.award.model.aggregate.UserAwardRecordAggregate;
import com.sjs.marketing.domain.award.model.entity.TaskEntity;
import com.sjs.marketing.domain.award.model.entity.UserAwardRecordEntity;
import com.sjs.marketing.domain.award.event.AwardCallbackMessage;
import com.sjs.marketing.domain.award.model.valobj.AwardDefineVO;
import com.sjs.marketing.domain.award.model.valobj.AwardDeliveryTypeVO;
import com.sjs.marketing.domain.award.model.valobj.UserAwardRecordVO;
import com.sjs.marketing.infrastructure.dao.IAwardDao;
import com.sjs.marketing.infrastructure.dao.IUserAwardRecordDao;
import com.sjs.marketing.infrastructure.dao.IUserRaffleOrderDao;
import com.sjs.marketing.infrastructure.dao.po.Award;
import com.sjs.marketing.infrastructure.dao.po.UserAwardRecord;
import com.sjs.marketing.infrastructure.dao.po.UserRaffleOrder;
import com.sjs.marketing.infrastructure.outbox.OutboxService;
import com.sjs.marketing.types.enums.ResponseCode;
import com.sjs.marketing.types.exception.AppException;
import com.sjs.marketing.types.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 中奖记录仓储。
 *
 * <p>中奖记录与发奖消息在同一个本地事务里落库：消息写入 {@code outbox}，
 * 由 {@link OutboxService} 的调度器异步投递，因此这里不再同步发 MQ。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Repository
public class AwardRepository implements IAwardRepository {

    @Resource
    private IUserAwardRecordDao userAwardRecordDao;
    @Resource
    private IUserRaffleOrderDao userRaffleOrderDao;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private OutboxService outboxService;
    @Resource
    private IAwardDao awardDao;

    @Override
    public void saveUserAwardRecord(UserAwardRecordAggregate userAwardRecordAggregate) {
        UserAwardRecordEntity userAwardRecordEntity = userAwardRecordAggregate.getUserAwardRecordEntity();
        TaskEntity taskEntity = userAwardRecordAggregate.getTaskEntity();
        String userId = userAwardRecordEntity.getUserId();
        Integer awardId = userAwardRecordEntity.getAwardId();
        Long activityId = userAwardRecordEntity.getActivityId();

        UserAwardRecord userAwardRecord = new UserAwardRecord();
        userAwardRecord.setUserId(userAwardRecordEntity.getUserId());
        userAwardRecord.setActivityId(userAwardRecordEntity.getActivityId());
        userAwardRecord.setStrategyId(userAwardRecordEntity.getStrategyId());
        userAwardRecord.setOrderId(userAwardRecordEntity.getOrderId());
        userAwardRecord.setAwardId(userAwardRecordEntity.getAwardId());
        userAwardRecord.setAwardTitle(userAwardRecordEntity.getAwardTitle());
        userAwardRecord.setAwardTime(userAwardRecordEntity.getAwardTime());
        userAwardRecord.setAwardState(userAwardRecordEntity.getAwardState().getCode());

        UserRaffleOrder userRaffleOrder = new UserRaffleOrder();
        userRaffleOrder.setUserId(userAwardRecordEntity.getUserId());
        userRaffleOrder.setOrderId(userAwardRecordEntity.getOrderId());

        String payload = JsonUtils.toJson(taskEntity.getMessage());

        // 把中奖结果翻译成给上游的交付指令：类型、数量、原文都在这里定
        AwardDefineVO define = queryAwardDefine(awardId);
        AwardDeliveryTypeVO deliveryType = AwardDeliveryTypeVO.of(define == null ? null : define.getAwardKey());
        int amount = AwardDeliveryTypeVO.resolveAmount(define == null ? null : define.getAwardConfig());

        String callbackEventId = UUID.randomUUID().toString().replace("-", "");
        AwardCallbackMessage callbackMessage = AwardCallbackMessage.builder()
                .eventId(callbackEventId)
                .orderId(userAwardRecordEntity.getOrderId())
                .userId(userId)
                .activityId(activityId)
                .awardId(awardId)
                .awardTitle(userAwardRecordEntity.getAwardTitle())
                .prizeType(deliveryType.getCode())
                .amount(amount)
                .config(define == null ? null : define.getAwardConfig())
                .awardKey(define == null ? null : define.getAwardKey())
                .build();
        String callbackPayload = JsonUtils.toJson(callbackMessage);

        // 分库分表由 ShardingSphere 依据 SQL 中的 user_id 自动路由
        transactionTemplate.execute(status -> {
            try {
                // 1. 中奖流水
                userAwardRecordDao.insert(userAwardRecord);

                // 2. 发奖消息进 outbox（与业务同事务，保证「业务成功 ⇒ 消息必达」）
                outboxService.append(userId, taskEntity.getTopic(), taskEntity.getMessageId(), payload);

                // 3. 发奖回调进 outbox：中台不持有资产，把「中了什么」推给上游去入账。
                //    与业务同事务，回调也不会凭空丢失；HTTP 投递失败由 outbox 重试。
                outboxService.appendCallback(userId, callbackEventId, callbackPayload);

                // 3. 抽奖单置为已使用，影响行数不为 1 说明已被消费，整笔回滚
                int updateCount = userRaffleOrderDao.updateUserRaffleOrderStateUsed(userRaffleOrder);
                if (updateCount != 1) {
                    log.error("更新抽奖记录失败 userId: {} activityId: {}", userId, activityId);
                    status.setRollbackOnly();
                    throw new AppException(ResponseCode.ACTIVITY_ORDER_ERROR.getCode(), ResponseCode.ACTIVITY_ORDER_ERROR.getInfo());
                }
                return 1;
            } catch (DuplicateKeyException e) {
                status.setRollbackOnly();
                log.error("写入中奖记录，唯一索引冲突 userId: {} activityId: {} awardId: {}", userId, activityId, awardId, e);
                throw new AppException(ResponseCode.INDEX_DUP.getCode(), e);
            }
        });

        log.info("中奖记录落库并写入 outbox userId: {} activityId: {} awardId: {}", userId, activityId, awardId);
    }

    @Override
    public List<UserAwardRecordVO> queryRecentAwards(int limit) {
        List<UserAwardRecord> records = userAwardRecordDao.queryRecent(limit);
        List<UserAwardRecordVO> result = new ArrayList<>(records.size());
        for (UserAwardRecord record : records) {
            result.add(UserAwardRecordVO.builder()
                    .userId(record.getUserId())
                    .activityId(record.getActivityId())
                    .strategyId(record.getStrategyId())
                    .orderId(record.getOrderId())
                    .awardId(record.getAwardId())
                    .awardTitle(record.getAwardTitle())
                    .awardTime(record.getAwardTime())
                    .awardState(record.getAwardState())
                    .build());
        }
        return result;
    }

    @Override
    public AwardDefineVO queryAwardDefine(Integer awardId) {
        Award award = awardDao.queryAwardDefine(awardId);
        if (award == null) {
            return null;
        }
        return AwardDefineVO.builder()
                .awardId(award.getAwardId())
                .awardKey(award.getAwardKey())
                .awardConfig(award.getAwardConfig())
                .awardDesc(award.getAwardDesc())
                .build();
    }

    @Override
    public void updateAwardState(String userId, String orderId, String state) {
        userAwardRecordDao.updateAwardState(userId, orderId, state);
    }
}
