package com.sjs.marketing.infrastructure.dao.po;

import lombok.Data;

import java.util.Date;

/**
 * 本地消息表（outbox）记录。
 *
 * <p>状态机：pending → sending → sent → consumed；失败重试 8 次后进入 dead。</p>
 *
 * @author marketing-engine contributors
 */
@Data
public class OutboxMessage {

    /** 自增ID */
    private Long id;
    /** 用户ID，参与分库路由 */
    private String userId;
    /** 消息主题 */
    private String topic;
    /** 消息唯一标识，消费端用它做幂等回执 */
    private String messageId;
    /** 消息体（JSON） */
    private String payload;
    /** 投递方式：MQ / HTTP */
    private String deliveryType;
    /** HTTP 投递目标地址（MQ 投递时为空） */
    private String target;
    /** 状态：pending/sending/sent/consumed/dead */
    private String state;
    /** 已重试次数 */
    private Integer retryCount;
    /** 下次可投递时间 */
    private Date nextRetryAt;
    /** 投递成功时间 */
    private Date sentAt;
    /** 消费回执时间 */
    private Date consumedAt;
    /** 创建时间 */
    private Date createdAt;
    /** 更新时间 */
    private Date updatedAt;

}
