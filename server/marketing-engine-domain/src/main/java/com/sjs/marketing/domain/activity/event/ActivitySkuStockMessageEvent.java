package com.sjs.marketing.domain.activity.event;

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
 * 活动 SKU 库存扣减事件。
 *
 * <p>消息体携带的是「扣减后的累计已扣数」（绝对量），不是「减 1」的增量。
 * 消费端据此把 DB 库存写成 {@code 总量 - 已扣数}，重复投递与乱序到达都不会算错。</p>
 *
 * @author marketing-engine contributors
 */
@Component
public class ActivitySkuStockMessageEvent extends BaseEvent<ActivitySkuStockMessageEvent.ActivitySkuStockMessage> {

    @Value("${spring.rabbitmq.topic.activity_sku_stock}")
    private String topic;

    @Override
    public EventMessage<ActivitySkuStockMessage> buildEventMessage(ActivitySkuStockMessage data) {
        return EventMessage.<ActivitySkuStockMessage>builder()
                .id(RandomStringUtils.randomNumeric(11))
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
    public static class ActivitySkuStockMessage {
        /** 用户ID（分片键） */
        private String userId;
        /** 活动ID */
        private Long activityId;
        /** SKU */
        private Long sku;
        /** 扣减后的累计已扣数 */
        private Integer consumedAfter;
    }

}
