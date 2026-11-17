package com.sjs.marketing.trigger.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sjs.marketing.domain.outbox.service.IOutboxAckService;
import com.sjs.marketing.domain.pay.event.PayGrantEvent;
import com.sjs.marketing.domain.pay.service.IPayService;
import com.sjs.marketing.types.event.BaseEvent;
import com.sjs.marketing.types.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 支付发放消息消费者。
 *
 * <p>回调只负责「钱到了」，真正的权益发放由这里异步做：
 * 消费失败会让 outbox 重投，重投时靠 {@code eventId=pay:{orderNo}} 幂等，
 * 所以「断网重试仍然只到账一次」。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Component
public class PayGrantCustomer {

    @Resource
    private IPayService payService;

    @Resource
    private IOutboxAckService outboxService;

    @RabbitListener(queuesToDeclare = @Queue(value = "${spring.rabbitmq.topic.pay_grant:pay_grant}"))
    public void listener(String message) {
        BaseEvent.EventMessage<PayGrantEvent.PayGrantMessage> eventMessage = JsonUtils.parse(message,
                new TypeReference<BaseEvent.EventMessage<PayGrantEvent.PayGrantMessage>>() {
                });
        PayGrantEvent.PayGrantMessage data = eventMessage.getData();
        try {
            boolean granted = payService.grantPaidOrder(data.getOrderNo(), data.getUserId());
            if (!granted) {
                log.error("支付发放失败，等待重投 orderNo:{}", data.getOrderNo());
                throw new IllegalStateException("支付发放失败：" + data.getOrderNo());
            }
            outboxService.markConsumed(data.getUserId(), eventMessage.getId());
            log.info("支付权益已发放 orderNo:{} userId:{} amount:{}", data.getOrderNo(), data.getUserId(), data.getAmount());
        } catch (Exception e) {
            log.error("支付发放消息处理异常 orderNo:{} message:{}", data.getOrderNo(), message, e);
            throw e;
        }
    }
}
