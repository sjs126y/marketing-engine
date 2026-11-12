package com.sjs.marketing.trigger.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sjs.marketing.domain.activity.model.entity.SkuRechargeEntity;
import com.sjs.marketing.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.sjs.marketing.domain.rebate.event.SendRebateMessageEvent;
import com.sjs.marketing.domain.rebate.model.valobj.RebateTypeVO;
import com.sjs.marketing.domain.outbox.service.IOutboxAckService;
import com.sjs.marketing.types.enums.ResponseCode;
import com.sjs.marketing.types.event.BaseEvent;
import com.sjs.marketing.types.exception.AppException;
import com.sjs.marketing.types.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 返利消息消费者。
 *
 * <p>把返利配置里的 sku 交给额度服务入账，成功后向 outbox 回执。
 * 重复消费由 {@code raffle_activity_order.out_business_no} 唯一键兜底，
 * 这里把 {@code INDEX_DUP} 当作正常路径处理。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Component
public class RebateMessageCustomer {

    @Value("${spring.rabbitmq.topic.send_rebate}")
    private String topic;

    @Resource
    private IRaffleActivityAccountQuotaService raffleActivityAccountQuotaService;

    @Resource
    private IOutboxAckService outboxService;

    @RabbitListener(queuesToDeclare = @Queue(value = "${spring.rabbitmq.topic.send_rebate}"))
    public void listener(String message) {
        BaseEvent.EventMessage<SendRebateMessageEvent.RebateMessage> eventMessage =
                JsonUtils.parse(message, new TypeReference<BaseEvent.EventMessage<SendRebateMessageEvent.RebateMessage>>() {
                });
        SendRebateMessageEvent.RebateMessage rebateMessage = eventMessage.getData();
        String userId = rebateMessage.getUserId();

        try {
            log.info("监听用户行为返利消息 topic: {} userId: {} rebateType: {}", topic, userId, rebateMessage.getRebateType());

            if (!RebateTypeVO.SKU.getCode().equals(rebateMessage.getRebateType())) {
                // 非 sku 类型（如积分）目前只有落单没有入账，直接回执避免无限重投
                log.info("监听用户行为返利消息 - 非sku奖励暂不处理，已回执 topic: {} message: {}", topic, message);
                outboxService.markConsumed(userId, eventMessage.getId());
                return;
            }

            SkuRechargeEntity skuRechargeEntity = new SkuRechargeEntity();
            skuRechargeEntity.setUserId(userId);
            skuRechargeEntity.setSku(Long.valueOf(rebateMessage.getRebateConfig()));
            skuRechargeEntity.setOutBusinessNo(rebateMessage.getBizId());

            raffleActivityAccountQuotaService.createOrder(skuRechargeEntity);
            outboxService.markConsumed(userId, eventMessage.getId());
        } catch (AppException e) {
            if (ResponseCode.INDEX_DUP.getCode().equals(e.getCode())) {
                log.warn("监听用户行为返利消息，消费重复（已入账） userId: {} messageId: {}", userId, eventMessage.getId());
                outboxService.markConsumed(userId, eventMessage.getId());
                return;
            }
            log.error("监听用户行为返利消息，消费失败 topic: {} message: {}", topic, message, e);
            throw e;
        } catch (Exception e) {
            log.error("监听用户行为返利消息，消费失败 topic: {} message: {}", topic, message, e);
            throw e;
        }
    }
}
