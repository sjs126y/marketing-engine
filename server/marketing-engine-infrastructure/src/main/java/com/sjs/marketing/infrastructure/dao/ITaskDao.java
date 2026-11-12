package com.sjs.marketing.infrastructure.dao;
import com.sjs.marketing.infrastructure.dao.po.Task;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @author marketing-engine contributors
 * @description 任务表，发送MQ
 * @create 2024-04-03 15:57
 */
@Mapper
public interface ITaskDao {
    List<Task> queryNoSendMessageTaskList();

    void insert(Task task);
    void updateTaskSendMessageCompleted(Task task);
    void updateTaskSendMessageFail(Task task);
}
