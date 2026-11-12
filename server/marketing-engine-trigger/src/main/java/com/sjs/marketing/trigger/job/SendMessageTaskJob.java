package com.sjs.marketing.trigger.job;

import com.sjs.marketing.domain.task.model.entity.TaskEntity;
import com.sjs.marketing.domain.task.service.ITaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 扫描 task 表，补偿投递 MQ 消息。
 *
 * <p>分库分表交给 ShardingSphere：task 表按 user_id 分库，这里不带分片键查询，
 * ShardingSphere 会广播到所有分片并归并结果；带 user_id 的更新则精确路由。
 * 因此不再需要手工遍历数据库，也不会出现异步线程丢失路由的问题。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Component
public class SendMessageTaskJob {

    @Resource
    private ITaskService taskService;

    @Resource
    private ThreadPoolExecutor executor;

    @Scheduled(cron = "0/5 * * * * *")
    public void execute() {
        try {
            List<TaskEntity> taskEntities = taskService.queryNoSendMessageTaskList();
            if (taskEntities.isEmpty()) {
                return;
            }

            for (TaskEntity taskEntity : taskEntities) {
                executor.execute(() -> {
                    try {
                        taskService.sendMessage(taskEntity);
                        taskService.updateTaskSendMessageCompleted(taskEntity.getUserId(), taskEntity.getMessageId());
                    } catch (Exception e) {
                        log.error("定时任务，发送MQ消息失败 userId: {} topic: {}", taskEntity.getUserId(), taskEntity.getTopic());
                        taskService.updateTaskSendMessageFail(taskEntity.getUserId(), taskEntity.getMessageId());
                    }
                });
            }
        } catch (Exception e) {
            log.error("定时任务，扫描 MQ 任务表发送消息失败。", e);
        }
    }

}
