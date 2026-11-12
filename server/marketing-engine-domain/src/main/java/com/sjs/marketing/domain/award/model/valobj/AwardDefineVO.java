package com.sjs.marketing.domain.award.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 奖品定义（award 表）。
 *
 * <p>{@code awardKey} 决定「这个奖品怎么交付」：积分、对话额度还是模型解锁；
 * {@code awardConfig} 是交付参数（金额/次数/模型名）。中台据此算出回调里要告诉上游的
 * {@code prizeType} 与 {@code amount}。</p>
 *
 * @author marketing-engine contributors
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AwardDefineVO {

    /** 奖品ID */
    private Integer awardId;
    /** 发奖渠道标识 */
    private String awardKey;
    /** 发奖配置 */
    private String awardConfig;
    /** 奖品描述 */
    private String awardDesc;

}
