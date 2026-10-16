package com.sjs.marketing.api.dto;

import lombok.Data;

/**
 * @author marketing-engine contributors
 * @description 用户活动账户请求对象
 * @create 2024-05-03 07:17
 */
@Data
public class UserActivityAccountRequestDTO {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 活动ID
     */
    private Long activityId;

}
