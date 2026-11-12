package com.sjs.marketing.trigger.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sjs.marketing.domain.award.event.SendAwardMessageEvent;
import com.sjs.marketing.domain.outbox.service.IOutboxAckService;
import com.sjs.marketing.types.event.BaseEvent;
import com.sjs.marketing.types.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 发奖消息消费者。
 *
 * <p>消费成功后向 outbox 回执（sent → consumed），失败则抛出异常让 MQ 重新投递。
 * 真正的发奖渠道对接是本项目的已知缺口：当前只记录日志并完成回执闭环。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Component
public class SendAwardCustomer {

    @Value("${spring.rabbitmq.topic.send_award}")
    private String topic;

    @Resource
    private IOutboxAckService outboxService;

    @RabbitListener(queuesToDeclare = @Queue(value = "${spring.rabbitmq.topic.send_award}"))
    public void listener(String message) {
        BaseEvent.EventMessage<SendAwardMessageEvent.SendAwardMessage> eventMessage =
                JsonUtils.parse(message, new TypeReference<BaseEvent.EventMessage<SendAwardMessageEvent.SendAwardMessage>>() {
                });
        try {
            SendAwardMessageEvent.SendAwardMessage data = eventMessage.getData();
            log.info("监听用户奖品发送消息 topic: {} userId: {} awardId: {} awardTitle: {}",
                    topic, data.getUserId(), data.getAwardId(), data.getAwardTitle());

            // 发奖渠道对接在此处扩展；当前仅完成消费与回执闭环
            outboxService.markConsumed(data.getUserId(), eventMessage.getId());
        } catch (Exception e) {
            log.error("监听用户奖品发送消息，消费失败 topic: {} message: {}", topic, message, e);
            throw e;
        }
    }
}
