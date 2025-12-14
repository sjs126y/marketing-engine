package com.sjs.infrastructure.dao;

import com.sjs.domain.activity.model.entity.ActivitySkuEntity;
import com.sjs.infrastructure.dao.po.RaffleActivitySku;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface IRaffleActivitySkuDao {

    RaffleActivitySku queryActivitySku(Long activityId);

    void updateActivitySkuStockCount(Long sku);

    void clearActivitySkuStockCount(Long sku);

    List<RaffleActivitySku> queryActivitySkuListByActivityId(Long activityId);
}
