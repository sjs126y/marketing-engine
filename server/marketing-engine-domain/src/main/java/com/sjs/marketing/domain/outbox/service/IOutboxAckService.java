package com.sjs.marketing.domain.outbox.service;

/**
 * 可靠投递的消费回执端口。
 *
 * <p>消息消费成功后调用，把本地消息表中的记录从 sent 推进到 consumed，
 * 让「已投递」和「已生效」这两件事都能被观测到。</p>
 *
 * <p>实现位于基础设施层（outbox 属于技术设施），领域与触发器只依赖这个端口，
 * 以保持「domain 不依赖 MyBatis/Redis/MQ」的约定。</p>
 *
 * @author marketing-engine contributors
 */
public interface IOutboxAckService {

    /**
     * 消费回执。
     *
     * @param userId    消息所属用户，用于路由到正确分片
     * @param messageId 消息唯一 ID
     */
    void markConsumed(String userId, String messageId);

    /**
     * 当前待投递消息数，用于看板与告警。
     */
    int pendingCount();

}
