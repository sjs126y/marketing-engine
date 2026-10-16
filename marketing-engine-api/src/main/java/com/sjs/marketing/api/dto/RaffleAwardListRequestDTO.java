package com.sjs.marketing.api.dto;

import lombok.Data;

/**
 * @author marketing-engine contributors
 * @description 抽奖奖品列表，请求对象
 * @create 2024-02-14 09:46
 */
@Data
public class RaffleAwardListRequestDTO {


    // 用户ID
    private String userId;
    // 抽奖活动ID
    private Long activityId;

}
