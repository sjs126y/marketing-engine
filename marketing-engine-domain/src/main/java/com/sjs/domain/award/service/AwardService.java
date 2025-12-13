package com.sjs.domain.award.service;

import com.alibaba.fastjson.JSON;
import com.sjs.domain.award.adapter.repository.IAwardRepository;
import com.sjs.domain.award.event.SendAwardMessageEvent;
import com.sjs.domain.award.model.aggregate.UserAwardRecordAggregate;
import com.sjs.domain.award.model.entity.TaskEntity;
import com.sjs.domain.award.model.entity.UserAwardRecordEntity;
import com.sjs.domain.award.model.valobj.TaskStateVO;
import com.sjs.types.event.BaseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class AwardService implements IAwardService {

    @Resource
    private SendAwardMessageEvent sendAwardMessageEvent;
    @Resource
    private IAwardRepository repository;

    @Override
    public void saveUserAwardRecord(UserAwardRecordEntity userAwardRecordEntity) {
        log.info("发送用户中奖记录 userAwardRecordEntity ：{}", userAwardRecordEntity);

        //1.构建发送消息
        SendAwardMessageEvent.SendAwardMessage sendAwardMessage = new SendAwardMessageEvent.SendAwardMessage();
        sendAwardMessage.setUserId(userAwardRecordEntity.getUserId());
        sendAwardMessage.setAwardId(userAwardRecordEntity.getAwardId());
        sendAwardMessage.setAwardTitle(userAwardRecordEntity.getAwardTitle());

        BaseEvent.EventMessage<SendAwardMessageEvent.SendAwardMessage> sendAwardMessageEventMessage = sendAwardMessageEvent.buildEventMessage(sendAwardMessage);

        //2.构建任务
        TaskEntity taskEntity = new TaskEntity();
        taskEntity.setUserId(userAwardRecordEntity.getUserId());
        taskEntity.setTopic(sendAwardMessageEvent.topic());
        taskEntity.setMessageId(sendAwardMessageEventMessage.getId());
        taskEntity.setMessage(sendAwardMessageEventMessage);
        taskEntity.setState(TaskStateVO.create);

        UserAwardRecordAggregate userAwardRecordAggregate = UserAwardRecordAggregate.builder()
                .userAwardRecordEntity(userAwardRecordEntity)
                .taskEntity(taskEntity)
                .build();

        // 存储聚合对象 - 一个事务下，用户的中奖记录
        repository.saveUserAwardRecord(userAwardRecordAggregate);
    }
}
