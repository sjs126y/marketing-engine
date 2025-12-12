package com.sjs.infrastructure.dao;

import com.sjs.infrastructure.dao.po.RaffleActivitySku;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IRaffleActivitySkuDao {

    RaffleActivitySku queryActivitySku(Long activityId);

}
