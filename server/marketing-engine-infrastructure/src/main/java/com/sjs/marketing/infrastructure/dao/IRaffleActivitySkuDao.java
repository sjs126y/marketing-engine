package com.sjs.marketing.infrastructure.dao;

import com.sjs.marketing.infrastructure.dao.po.RaffleActivitySku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IRaffleActivitySkuDao {

    RaffleActivitySku queryActivitySku(Long activityId);

    /**
     * 以绝对量回写库存：只有更大的累计已扣数才会生效（单调保护），
     * 因此重复消费与乱序到达都是幂等的。
     *
     * @param sku           SKU
     * @param consumedAfter 扣减后的累计已扣数
     * @return 影响行数，0 表示消息过期（已有更大的值）或 SKU 不存在
     */
    int updateAppliedConsumed(@Param("sku") Long sku, @Param("consumedAfter") Integer consumedAfter);

    List<RaffleActivitySku> queryAllSkuStock();

    List<RaffleActivitySku> queryActivitySkuListByActivityId(Long activityId);
}
