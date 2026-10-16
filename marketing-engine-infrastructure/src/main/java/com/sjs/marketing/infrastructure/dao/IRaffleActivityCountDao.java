package com.sjs.marketing.infrastructure.dao;

import com.sjs.marketing.infrastructure.dao.po.RaffleActivityCount;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author marketing-engine contributors
 * @description 抽奖活动次数配置表Dao
 * @create 2024-03-09 10:07
 */
@Mapper
public interface IRaffleActivityCountDao {
    RaffleActivityCount queryRaffleActivityCountByActivityCountId(Long activityCountId);
}
