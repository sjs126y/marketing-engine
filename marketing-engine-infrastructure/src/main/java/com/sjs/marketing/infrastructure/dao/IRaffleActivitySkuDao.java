package com.sjs.marketing.infrastructure.dao;

import com.sjs.marketing.domain.activity.model.entity.ActivitySkuEntity;
import com.sjs.marketing.infrastructure.dao.po.RaffleActivitySku;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface IRaffleActivitySkuDao {

    RaffleActivitySku queryActivitySku(Long activityId);

    void updateActivitySkuStockCount(Long sku);

    void clearActivitySkuStockCount(Long sku);

    List<RaffleActivitySku> queryActivitySkuListByActivityId(Long activityId);
}
