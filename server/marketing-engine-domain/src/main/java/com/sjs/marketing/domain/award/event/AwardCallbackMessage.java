package com.sjs.marketing.domain.award.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发奖回调消息：中台告诉上游「这个用户中了什么」。
 *
 * <p>中台不持有积分、对话次数这些业务资产，只把交付指令推给上游，
 * 由上游自己入账。{@code eventId} 是幂等键，上游据此去重。</p>
 *
 * @author marketing-engine contributors
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AwardCallbackMessage {

    /** 回调消息的 topic（outbox 里用它区分投递内容） */
    public static final String TOPIC = "award.callback";

    /** 幂等键，同时作为 HTTP 头 X-Event-Id */
    private String eventId;
    /** 抽奖单号，中台据此把发奖状态推进到 complete */
    private String orderId;
    /** 用户ID */
    private String userId;
    /** 活动ID */
    private Long activityId;
    /** 奖品ID */
    private Integer awardId;
    /** 奖品名称 */
    private String awardTitle;
    /** 交付类型：CREDIT / LLM_QUOTA / ENTITLEMENT */
    private String prizeType;
    /** 交付数量 */
    private Integer amount;
    /** 原始配置（模型名等非数值配置放这里） */
    private String config;
    /** 发奖渠道标识 */
    private String awardKey;

}
