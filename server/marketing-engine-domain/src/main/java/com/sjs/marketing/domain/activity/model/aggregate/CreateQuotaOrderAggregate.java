package com.sjs.marketing.domain.activity.model.aggregate;

import com.sjs.marketing.domain.activity.model.entity.ActivityOrderEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author marketing-engine contributors
 * @description 下单聚合对象
 * @create 2024-03-16 10:32
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateQuotaOrderAggregate {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 活动ID
     */
    private Long activityId;

    /**
     * 增加；总次数
     */
    private Integer totalCount;

    /**
     * 增加；日次数
     */
    private Integer dayCount;

    /**
     * 增加；月次数
     */
    private Integer monthCount;

    /**
     * 活动订单实体
     */
    private ActivityOrderEntity activityOrderEntity;

    /**
     * 本次扣减后的 SKU 累计已扣数（Redis 口径）。
     *
     * <p>落库时作为 outbox 消息的内容，让消费端以绝对量回写库存——
     * 重复投递或乱序到达都不会把库存算错。</p>
     */
    private Integer skuConsumedAfter;

}
