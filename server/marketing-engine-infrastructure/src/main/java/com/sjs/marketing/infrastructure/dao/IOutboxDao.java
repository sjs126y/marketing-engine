package com.sjs.marketing.infrastructure.dao;

import com.sjs.marketing.infrastructure.dao.po.OutboxMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 本地消息表（outbox）数据访问。
 *
 * <p>关键约定：outbox 按 {@code user_id} 分库，自增 {@code id} 在各分片内独立编号，
 * **同一个 id 在 ds1 和 ds2 都存在**。所以任何按主键的更新都必须带上 {@code user_id}，
 * 否则 ShardingSphere 会把它当无分片键 SQL 广播到所有库，误改另一个分片的行。
 * 这里统一用 {@code (user_id, message_id)} 定位，两者都在查询结果里，天然精确路由。</p>
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
    int claim(@Param("userId") String userId, @Param("messageId") String messageId);

    int markSent(@Param("userId") String userId, @Param("messageId") String messageId);

    int markRetry(@Param("userId") String userId, @Param("messageId") String messageId,
                  @Param("nextRetryAt") Date nextRetryAt);

    int markDead(@Param("userId") String userId, @Param("messageId") String messageId);

    /**
     * 消费回执：sent/pending → consumed。
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
