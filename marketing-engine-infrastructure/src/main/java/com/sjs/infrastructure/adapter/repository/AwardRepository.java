package com.sjs.infrastructure.adapter.repository;

import cn.bugstack.middleware.db.router.strategy.IDBRouterStrategy;
import com.alibaba.fastjson.JSON;
import com.sjs.domain.award.adapter.repository.IAwardRepository;
import com.sjs.domain.award.model.aggregate.UserAwardRecordAggregate;
import com.sjs.domain.award.model.entity.TaskEntity;
import com.sjs.domain.award.model.entity.UserAwardRecordEntity;
import com.sjs.infrastructure.dao.ITaskDao;
import com.sjs.infrastructure.dao.IUserAwardRecordDao;
import com.sjs.infrastructure.dao.IUserRaffleOrderDao;
import com.sjs.infrastructure.dao.po.Task;
import com.sjs.infrastructure.dao.po.UserAwardRecord;
import com.sjs.infrastructure.dao.po.UserRaffleOrder;
import com.sjs.infrastructure.event.EventPublisher;
import com.sjs.types.enums.ResponseCode;
import com.sjs.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;

@Slf4j
@Repository
public class AwardRepository implements IAwardRepository {

    @Resource
    private ITaskDao taskDao;
    @Resource
    private IUserAwardRecordDao userAwardRecordDao;
    @Resource
    private IUserRaffleOrderDao userRaffleOrderDao;
    @Resource
    private IDBRouterStrategy dbRouter;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private EventPublisher eventPublisher;

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

        Task task = new Task();
        task.setUserId(taskEntity.getUserId());
        task.setTopic(taskEntity.getTopic());
        task.setMessageId(taskEntity.getMessageId());
        task.setMessage(JSON.toJSONString(taskEntity.getMessage()));
        task.setState(taskEntity.getState().getCode());

        UserRaffleOrder userRaffleOrder = new UserRaffleOrder();
        userRaffleOrder.setUserId(userAwardRecordEntity.getUserId());
        userRaffleOrder.setOrderId(userAwardRecordEntity.getOrderId());

        try {
            dbRouter.doRouter(userId);
            transactionTemplate.execute(status -> {
                try{
                    //先插入中奖流水记录
                    userAwardRecordDao.insert(userAwardRecord);

                    //再插入任务表
                    taskDao.insert(task);

                    //更新抽奖记录
                    int updateCount = userRaffleOrderDao.updateUserRaffleOrderStateUsed(userRaffleOrder);
                    if(updateCount != 1){
                        log.error("更新抽奖记录失败 userId: {} activityId: {}", userId, activityId);
                        status.setRollbackOnly();
                        throw new AppException(ResponseCode.ACTIVITY_ORDER_ERROR.getCode(), ResponseCode.ACTIVITY_ORDER_ERROR.getInfo());
                    }
                return 1;
                } catch (DuplicateKeyException e) {
                    status.setRollbackOnly();
                    log.error("写入中奖记录，唯一索引冲突 userId: {} activityId: {} awardId: {}", userId, activityId, awardId, e);
                    throw new AppException(ResponseCode.INDEX_DUP.getCode(), e);            }
            });
        } finally {
            dbRouter.clear();
        }

        //发送mq消息
        try{
            eventPublisher.publish(task.getTopic(), task.getMessage());
            // 更新数据库记录，task 任务表
            taskDao.updateTaskSendMessageCompleted(task);
            log.info("发送MQ消息成功 userId: {} activityId: {} awardId: {}", userId, activityId, awardId);
        } catch (Exception e) {
            log.error("发送MQ消息失败 userId: {} activityId: {} awardId: {}", userId, activityId, awardId, e);
            taskDao.updateTaskSendMessageFail(task);
        }

    }
}
