package com.sjs.domain.activity.model.entity;

import lombok.Data;

/**
 * @author marketing-engine contributors
 * @description 参与抽奖活动实体对象
 * @create 2024-04-04 20:02
 */
@Data
public class PartakeRaffleActivityEntity {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 活动ID
     */
    private Long activityId;

}
