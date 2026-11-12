package com.sjs.marketing.infrastructure.outbox;

import com.sjs.marketing.domain.award.event.AwardCallbackMessage;
import com.sjs.marketing.domain.award.service.IAwardService;
import com.sjs.marketing.types.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 回调送达后的状态推进：把中奖记录的 {@code award_state} 从 create 推到 complete。
 *
 * <p>单独成一个监听器，避免 OutboxService 反过来依赖领域服务造成环依赖。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Component
public class AwardCallbackDeliveredListener {

    @Resource
    private IAwardService awardService;

    @EventListener
    public void onDelivered(AwardCallbackDeliveredEvent event) {
        try {
            awardService.markDelivered(event.userId(), event.orderId());
            log.info("发奖回调已送达，状态推进为 complete userId:{} orderId:{}",
                    event.userId(), event.orderId());
        } catch (Exception e) {
            // 回调已经送达，状态更新失败不影响事实，交给对账兜底
            log.warn("发放状态更新失败 userId:{} orderId:{}", event.userId(), event.orderId(), e);
        }
    }

    /**
     * 从回调 payload 解析出事件，供投递器调用。
     */
    public static AwardCallbackDeliveredEvent parse(String payload) {
        AwardCallbackMessage message = JsonUtils.parse(payload, AwardCallbackMessage.class);
        if (message == null || message.getUserId() == null || message.getOrderId() == null) {
            return null;
        }
        return new AwardCallbackDeliveredEvent(message.getUserId(), message.getOrderId());
    }

}
