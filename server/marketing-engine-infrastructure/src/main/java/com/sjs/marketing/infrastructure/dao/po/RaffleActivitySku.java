package com.sjs.marketing.infrastructure.dao.po;

import lombok.Data;

import java.util.Date;

/**
 * @author marketing-engine contributors
 * @description 抽奖活动sku持久化对象
 * @create 2024-03-16 10:54
 */
@Data
public class RaffleActivitySku {

    /**
     * 商品sku
     */
    private Long sku;
    /**
     * 活动ID
     */
    private Long activityId;
    /**
     * 活动个人参与次数ID
     */
    private Long activityCountId;
    /**
     * 库存总量
     */
    private Integer stockCount;
    /**
     * 剩余库存
     */
    private Integer stockCountSurplus;
    /**
     * 已写入数据库的累计已扣数（单调递增，用于绝对量回写的幂等保护）
     */
    private Integer appliedConsumed;
    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

}
