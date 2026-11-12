package com.sjs.marketing.trigger.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sjs.marketing.domain.activity.event.ActivitySkuStockMessageEvent;
import com.sjs.marketing.domain.activity.service.IActivityStockService;
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
 * 活动 SKU 库存回写消费者。
 *
 * <p>消息里带的是「扣减后的累计已扣数」，消费端按「库存 = 总量 - 已扣数」
 * 做绝对量回写，并由 SQL 的 {@code applied_consumed < consumedAfter} 保证单调。
 * 因此重复投递、乱序到达都不会把库存算错，重投不需要额外的去重表。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Component
public class ActivitySkuStockCustomer {

    @Value("${spring.rabbitmq.topic.activity_sku_stock}")
    private String topic;

    @Resource
    private IActivityStockService activityStockService;

    @Resource
    private IOutboxAckService outboxAckService;

    @RabbitListener(queuesToDeclare = @Queue(value = "${spring.rabbitmq.topic.activity_sku_stock}"))
    public void listener(String message) {
        BaseEvent.EventMessage<ActivitySkuStockMessageEvent.ActivitySkuStockMessage> eventMessage =
                JsonUtils.parse(message, new TypeReference<BaseEvent.EventMessage<ActivitySkuStockMessageEvent.ActivitySkuStockMessage>>() {
                });
        ActivitySkuStockMessageEvent.ActivitySkuStockMessage data = eventMessage.getData();

        try {
            log.info("监听 SKU 库存回写消息 topic: {} userId: {} sku: {} consumedAfter: {}",
                    topic, data.getUserId(), data.getSku(), data.getConsumedAfter());

            activityStockService.applyConsumed(data.getSku(), data.getConsumedAfter());
            outboxAckService.markConsumed(data.getUserId(), eventMessage.getId());
        } catch (Exception e) {
            log.error("监听 SKU 库存回写消息，消费失败 sku: {} consumedAfter: {}", data.getSku(), data.getConsumedAfter(), e);
            throw e;
        }
    }
}
