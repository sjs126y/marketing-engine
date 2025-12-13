package com.sjs.trigger.job;

import cn.bugstack.middleware.db.router.strategy.IDBRouterStrategy;
import com.sjs.domain.task.model.entity.TaskEntity;
import com.sjs.domain.task.service.ITaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Component
public class SendMessageTaskJob {

    @Resource
    private ITaskService taskService;
    @Resource
    private ThreadPoolExecutor executor;
    @Resource
    private IDBRouterStrategy dbRouter;

    @Scheduled(cron = "0/5 * * * * *")
    public void execute() {
        try {
            log.info("定时任务，扫描MQ任务表发送消息开始。");
            int dbCount = dbRouter.dbCount();
            for (int i = 1; i < dbCount; i++) {
                int finalDbIndex = i;
                executor.execute(() -> {
                    try {
                        dbRouter.setDBKey(finalDbIndex);
                        dbRouter.setTBKey(0);

                        List<TaskEntity> taskEntities = taskService.queryNoSendMessageTaskList();
                        if (taskEntities.isEmpty()) return;

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
                    } finally {
                        dbRouter.clear();
                    }
                });
            }

        } catch (Exception e) {
            log.error("定时任务，扫描MQ任务表发送消息失败。", e);
        }
    }

}
