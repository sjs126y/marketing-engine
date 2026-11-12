package com.sjs.marketing.infrastructure.outbox;

import com.sjs.marketing.infrastructure.dao.IOutboxDao;
import com.sjs.marketing.infrastructure.dao.po.OutboxMessage;
import com.sjs.marketing.domain.outbox.service.IOutboxAckService;
import com.sjs.marketing.infrastructure.event.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 本地消息表的读写与投递。
 *
 * <p>核心约定：</p>
 * <ul>
 *   <li>业务事务内只做 {@link #append}，保证「业务成功 ⇒ 消息一定入库」；</li>
 *   <li>投递是异步的，先用条件更新把消息抢占成 sending，再发 MQ，避免多实例重复投递；</li>
 *   <li>失败按指数退避重试，超过上限进入 dead，等待人工介入；</li>
 *   <li>消费端处理完成后调用 {@link #markConsumed} 回执，形成闭环。</li>
 * </ul>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Service
public class OutboxService implements IOutboxAckService {

    @Resource
    private IOutboxDao outboxDao;

    @Resource
    private EventPublisher eventPublisher;

    @Resource
    private OutboxProperties properties;

    /**
     * 在业务事务内追加一条待投递消息。
     */
    public void append(String userId, String topic, String messageId, String payload) {
        OutboxMessage message = new OutboxMessage();
        message.setUserId(userId);
        message.setTopic(topic);
        message.setMessageId(messageId);
        message.setPayload(payload);
        message.setState(OutboxState.PENDING.getCode());
        outboxDao.insert(message);
    }

    /**
     * 扫一批待投递消息并投递。
     *
     * @return 本轮投递成功的条数
     */
    public int dispatchOnce() {
        List<OutboxMessage> batch = outboxDao.queryPendingBatch(properties.getBatchSize());
        if (batch.isEmpty()) {
            return 0;
        }

        int sent = 0;
        for (OutboxMessage message : batch) {
            // 抢占成功才投递，避免多实例重复发送
            if (outboxDao.claim(message.getId()) != 1) {
                continue;
            }

            try {
                eventPublisher.publish(message.getTopic(), message.getPayload());
                outboxDao.markSent(message.getId());
                sent++;
            } catch (Exception e) {
                handleFailure(message, e);
            }
        }

        if (sent > 0) {
            log.info("outbox 投递完成，成功 {} 条，本批 {} 条", sent, batch.size());
        }
        return sent;
    }

    /**
     * 消费回执。消费端处理成功后调用，幂等由 message_id 保证。
     *
     * @param userId    消息所属用户，用于路由到正确的分片
     * @param messageId 消息唯一 ID
     */
    @Override
    public void markConsumed(String userId, String messageId) {
        if (messageId == null || messageId.isEmpty() || userId == null || userId.isEmpty()) {
            return;
        }
        int updated = outboxDao.markConsumed(userId, messageId);
        if (updated == 0) {
            log.debug("outbox 回执未命中（可能已消费） userId:{} messageId: {}", userId, messageId);
        }
    }

    /**
     * 故障恢复：把卡在 sending 的消息退回 pending，把超时未回执的 sent 重新入队。
     */
    public void recover() {
        Date stuckDeadline = new Date(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(properties.getStuckSeconds()));
        int resetStuck = outboxDao.resetStuckSending(stuckDeadline);
        if (resetStuck > 0) {
            log.warn("outbox 崩溃恢复：{} 条 sending 消息退回 pending", resetStuck);
        }

        Date consumeDeadline = new Date(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(properties.getConsumeTimeoutSeconds()));
        int buried = outboxDao.buryExhaustedSent(consumeDeadline, properties.getMaxRetry());
        int resent = outboxDao.resetStaleSent(consumeDeadline, properties.getMaxRetry());
        if (resent > 0) {
            log.warn("outbox 超时未回执：{} 条重新入队，{} 条超过上限进入 dead", resent, buried);
        }
    }

    /**
     * 积压量，供监控/健康检查读取。
     */
    @Override
    public int pendingCount() {
        return outboxDao.countByState(OutboxState.PENDING.getCode());
    }

    private void handleFailure(OutboxMessage message, Exception e) {
        int nextRetryCount = message.getRetryCount() + 1;
        if (nextRetryCount >= properties.getMaxRetry()) {
            outboxDao.markDead(message.getId());
            log.error("outbox 投递失败达到上限，转入 dead id:{} topic:{} messageId:{}",
                    message.getId(), message.getTopic(), message.getMessageId(), e);
            return;
        }

        long backoffSeconds = Math.min(
                1L << Math.min(nextRetryCount, 10),
                properties.getMaxBackoffSeconds());
        Date nextRetryAt = new Date(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(backoffSeconds));
        outboxDao.markRetry(message.getId(), nextRetryAt);
        log.warn("outbox 投递失败，{} 秒后重试（第 {} 次） id:{} topic:{}",
                backoffSeconds, nextRetryCount, message.getId(), message.getTopic(), e);
    }

}
