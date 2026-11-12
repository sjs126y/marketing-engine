package com.sjs.marketing.infrastructure.dao;
import com.sjs.marketing.infrastructure.dao.po.RaffleActivityOrder;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @author marketing-engine contributors
 * @description 抽奖活动单Dao
 * @create 2024-03-09 10:08
 */
@Mapper
public interface IRaffleActivityOrderDao {
    void insert(RaffleActivityOrder raffleActivityOrder);
    List<RaffleActivityOrder> queryRaffleActivityOrderByUserId(String userId);

}
