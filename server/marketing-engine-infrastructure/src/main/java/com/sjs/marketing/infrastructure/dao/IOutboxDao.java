package com.sjs.marketing.infrastructure.dao;

import com.sjs.marketing.infrastructure.dao.po.OutboxMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 本地消息表（outbox）数据访问。
 *
 * @author marketing-engine contributors
 */
@Mapper
public interface IOutboxDao {

    int insert(OutboxMessage message);

    /**
     * 取一批到期的待投递消息（不带锁，靠 claim 条件更新抢占，避免长事务持锁）。
     */
    List<OutboxMessage> queryPendingBatch(@Param("limit") int limit);

    /**
     * 抢占一条消息：pending → sending，返回 1 表示抢占成功。
     */
    int claim(@Param("id") Long id);

    int markSent(@Param("id") Long id);

    int markRetry(@Param("id") Long id, @Param("nextRetryAt") Date nextRetryAt);

    int markDead(@Param("id") Long id);

    /**
     * 消费回执：sent/pending → consumed。带上 user_id 以便精确路由到分片。
     */
    int markConsumed(@Param("userId") String userId, @Param("messageId") String messageId);

    /**
     * 崩溃恢复：sending 超过阈值仍未投递成功的消息退回 pending。
     */
    int resetStuckSending(@Param("deadline") Date deadline);

    /**
     * 投递后长时间未收到消费回执的消息重新入队（at-least-once，消费端需幂等）。
     */
    int resetStaleSent(@Param("deadline") Date deadline, @Param("maxRetry") int maxRetry);

    /**
     * 超过重试上限的 sent 消息置为 dead。
     */
    int buryExhaustedSent(@Param("deadline") Date deadline, @Param("maxRetry") int maxRetry);

    int countByState(@Param("state") String state);

}
