package com.sjs.marketing.infrastructure.dao;

import com.sjs.marketing.infrastructure.dao.po.RaffleActivity;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author marketing-engine contributors
 * @description 抽奖活动表Dao
 * @create 2024-03-09 10:04
 */
@Mapper
public interface IRaffleActivityDao {

    RaffleActivity queryRaffleActivityByActivityId(Long activityId);

    Long queryStrategyIdByActivityId(Long activityId);

    Long queryActivityIdByStrategyId(Long strategyId);
}
