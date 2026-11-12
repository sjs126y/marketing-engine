package com.sjs.marketing.domain.activity.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author marketing-engine contributors
 * @description 活动sku实体对象
 * @create 2024-03-16 10:29
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActivitySkuEntity {

    /** 商品sku */
    private Long sku;
    /** 活动ID */
    private Long activityId;
    /** 活动个人参数ID；在这个活动上，一个人可参与多少次活动（总、日、月） */
    private Long activityCountId;
    /** 库存总量 */
    private Integer stockCount;
    /** 剩余库存 */
    private Integer stockCountSurplus;
    /**
     * 本次扣减后的累计已扣数（Redis 口径）。
     *
     * <p>不是业务字段，而是扣减时留在实体上的「快照」：责任链扣完写进来，
     * 事务落库时据此把库存以绝对量回写，避免增量写法的重复消费问题。</p>
     */
    private Integer consumedAfter;

}
