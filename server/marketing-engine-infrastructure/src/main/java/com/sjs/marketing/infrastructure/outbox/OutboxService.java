package com.sjs.marketing.infrastructure.outbox;

import com.sjs.marketing.domain.award.event.AwardCallbackMessage;
import com.sjs.marketing.infrastructure.dao.IOutboxDao;
import com.sjs.marketing.infrastructure.dao.po.OutboxMessage;
import com.sjs.marketing.infrastructure.event.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
 *   <li>投递是异步的，先用条件更新把消息抢占成 sending，再投递，避免多实例重复；</li>
 *   <li>失败按指数退避重试，超过上限进入 dead，等待人工介入；</li>
 *   <li>消费端/回调成功后回执，形成闭环。</li>
 * </ul>
 *
 * <p>两种投递方式：</p>
 * <ul>
 *   <li>{@code MQ}：内部事件（发奖、返利、库存回写），投给 RabbitMQ；</li>
 *   <li>{@code HTTP}：对外回调（发奖结果通知上游），POST 到上游 webhook。</li>
 * </ul>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Service
public class OutboxService implements com.sjs.marketing.domain.outbox.service.IOutboxAckService {

    /** MQ 投递 */
    public static final String DELIVERY_MQ = "MQ";
    /** HTTP 回调 */
    public static final String DELIVERY_HTTP = "HTTP";

    @Resource
    private IOutboxDao outboxDao;

    @Resource
    private EventPublisher eventPublisher;

    @Resource
    private HttpCallbackPublisher httpCallbackPublisher;

    @Resource
    private AwardCallbackProperties callbackProperties;

    @Resource
    private OutboxProperties properties;

    @Resource
    private ApplicationEventPublisher applicationEventPublisher;

    /**
     * 在业务事务内追加一条 MQ 待投递消息。
     */
    public void append(String userId, String topic, String messageId, String payload) {
        append(userId, topic, messageId, payload, DELIVERY_MQ, null);
    }

    /**
     * 在业务事务内追加一条 HTTP 回调消息。
     *
     * <p>回调关闭时同样写库（只是不投递），便于开启后补发。</p>
     */
    public void appendCallback(String userId, String messageId, String payload) {
        append(userId, AwardCallbackMessage.TOPIC, messageId, payload,
                DELIVERY_HTTP, callbackProperties.getUrl());
    }

    private void append(String userId, String topic, String messageId, String payload,
                        String deliveryType, String target) {
        OutboxMessage message = new OutboxMessage();
        message.setUserId(userId);
        message.setTopic(topic);
        message.setMessageId(messageId);
        message.setPayload(payload);
        message.setDeliveryType(deliveryType);
        message.setTarget(target);
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
            if (!callbackProperties.isEnabled() && DELIVERY_HTTP.equals(message.getDeliveryType())) {
                // 回调未启用：消息留在 pending，开启后会自动补发
                continue;
            }
            if (outboxDao.claim(message.getUserId(), message.getMessageId()) != 1) {
                continue;
            }
            try {
                deliver(message);
                outboxDao.markSent(message.getUserId(), message.getMessageId());
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

    private void deliver(OutboxMessage message) throws Exception {
        if (DELIVERY_HTTP.equals(message.getDeliveryType())) {
            String target = message.getTarget() != null ? message.getTarget() : callbackProperties.getUrl();
            httpCallbackPublisher.publish(target, message.getMessageId(), message.getPayload());
            // 回调成功：把发奖状态推进到 complete
            if (AwardCallbackMessage.TOPIC.equals(message.getTopic())) {
                markDelivered(message.getPayload());
            }
            return;
        }
        eventPublisher.publish(message.getTopic(), message.getPayload());
    }

    private void markDelivered(String payload) {
        AwardCallbackDeliveredEvent event = AwardCallbackDeliveredListener.parse(payload);
        if (event != null) {
            // 用事件通知状态推进，避免 OutboxService 反向依赖领域服务形成环依赖
            applicationEventPublisher.publishEvent(event);
        }
    }

    /**
     * 消费回执。消费端处理成功后调用，幂等由 message_id 保证。
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

    @Override
    public int pendingCount() {
        return outboxDao.countByState(OutboxState.PENDING.getCode());
    }

    /**
     * 按投递方式统计积压，供看板区分「内部 MQ 卡住」还是「上游回调不通」。
     */
    public int pendingCount(String deliveryType) {
        return outboxDao.countByDeliveryType(deliveryType, OutboxState.PENDING.getCode());
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

    private void handleFailure(OutboxMessage message, Exception e) {
        int nextRetryCount = message.getRetryCount() + 1;
        if (nextRetryCount >= properties.getMaxRetry()) {
            outboxDao.markDead(message.getUserId(), message.getMessageId());
            log.error("outbox 投递失败达到上限，转入 dead id:{} topic:{} messageId:{}",
                    message.getId(), message.getTopic(), message.getMessageId(), e);
            return;
        }

        long backoffSeconds = Math.min(
                1L << Math.min(nextRetryCount, 10),
                properties.getMaxBackoffSeconds());
        Date nextRetryAt = new Date(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(backoffSeconds));
        outboxDao.markRetry(message.getUserId(), message.getMessageId(), nextRetryAt);
        log.warn("outbox 投递失败，{} 秒后重试（第 {} 次） topic:{} target:{} 原因:{}",
                backoffSeconds, nextRetryCount, message.getTopic(), message.getTarget(), e.getMessage());
    }

}
