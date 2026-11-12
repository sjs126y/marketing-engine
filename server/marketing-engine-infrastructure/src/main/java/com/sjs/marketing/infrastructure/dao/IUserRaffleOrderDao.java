package com.sjs.marketing.infrastructure.dao;
import com.sjs.marketing.infrastructure.dao.po.UserRaffleOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author marketing-engine contributors
 * @description 用户抽奖订单表
 * @create 2024-04-03 15:57
 */
@Mapper
public interface IUserRaffleOrderDao {
    UserRaffleOrder queryNoUsedRaffleOrder(UserRaffleOrder userRaffleOrderReq);

    void insert(UserRaffleOrder build);

    int updateUserRaffleOrderStateUsed(UserRaffleOrder userRaffleOrder);
}
