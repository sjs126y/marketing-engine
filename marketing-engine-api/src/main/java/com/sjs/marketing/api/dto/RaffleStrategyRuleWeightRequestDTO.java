package com.sjs.marketing.api.dto;

import lombok.Data;

/**
 * @author marketing-engine contributors
 * @description 抽奖策略规则，权重配置，查询N次抽奖可解锁奖品范围，请求对象
 * @create 2024-05-03 09:35
 */
@Data
public class RaffleStrategyRuleWeightRequestDTO {

    // 用户ID
    private String userId;
    // 抽奖活动ID
    private Long activityId;

}
