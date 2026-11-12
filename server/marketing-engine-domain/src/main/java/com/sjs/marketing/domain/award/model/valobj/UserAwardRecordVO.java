package com.sjs.marketing.domain.award.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 中奖记录视图对象，供运营看板展示。
 *
 * <p>{@code awardState} 目前永远是 {@code create}——发奖只做到了「落库 + 消息投递」，
 * 还没有真正的发奖渠道把状态推进到 {@code complete}。这一列是这条链路缺口的可视化。</p>
 *
 * @author marketing-engine contributors
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserAwardRecordVO {

    /** 用户ID */
    private String userId;
    /** 活动ID */
    private Long activityId;
    /** 策略ID */
    private Long strategyId;
    /** 抽奖单ID（幂等键） */
    private String orderId;
    /** 奖品ID */
    private Integer awardId;
    /** 奖品名称 */
    private String awardTitle;
    /** 中奖时间 */
    private Date awardTime;
    /** 发奖状态：create / complete / fail */
    private String awardState;

}
