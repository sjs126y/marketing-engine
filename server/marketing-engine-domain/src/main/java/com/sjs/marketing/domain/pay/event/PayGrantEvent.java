package com.sjs.marketing.domain.pay.event;

import com.sjs.marketing.types.event.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 支付成功后的发放事件。
 *
 * <p>为什么不在回调里直接发权益？支付是外部事实，发放是内部动作，
 * 中间隔着网络、DB、MQ，任何一步都可能挂。先落 outbox 再异步发，
 * 才能做到「钱收了，权益一定到账（或进死信被人看见）」。</p>
 *
 * @author marketing-engine contributors
 */
@Component
public class PayGrantEvent extends BaseEvent<PayGrantEvent.PayGrantMessage> {

    @Value("${spring.rabbitmq.topic.pay_grant:pay_grant}")
    private String topic;

    @Override
    public EventMessage<PayGrantMessage> buildEventMessage(PayGrantMessage data) {
        return EventMessage.<PayGrantMessage>builder()
                .id(RandomStringUtils.randomAlphanumeric(11))
                .timestamp(new Date())
                .data(data)
                .build();
    }

    @Override
    public String topic() {
        return topic;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PayGrantMessage {
        /** 订单号（发放幂等键） */
        private String orderNo;
        /** 用户ID */
        private String userId;
        /** 商品编码 */
        private String sku;
        /** 发放数量 */
        private Integer amount;
    }
}
