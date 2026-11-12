package com.sjs.marketing.infrastructure.outbox;

/**
 * 发奖回调已送达上游的事件。
 *
 * <p>用事件而不是直接调用领域服务，是为了打断一条环依赖：
 * {@code OutboxService → AwardService → AwardRepository → OutboxService}。
 * 投递器只负责投递，状态推进由监听器负责。</p>
 *
 * @param userId  用户ID
 * @param orderId 抽奖单号
 */
public record AwardCallbackDeliveredEvent(String userId, String orderId) {
}
