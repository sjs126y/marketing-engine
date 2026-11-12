package com.sjs.marketing.infrastructure.dao;
import com.sjs.marketing.infrastructure.dao.po.RaffleActivityAccountMonth;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author marketing-engine contributors
 * @description 抽奖活动账户表-月次数
 * @create 2024-04-03 15:57
 */
@Mapper
public interface IRaffleActivityAccountMonthDao {
    RaffleActivityAccountMonth queryActivityAccountMonthByUserId(RaffleActivityAccountMonth raffleActivityAccountMonthReq);

    int updateActivityAccountMonthSubtractionQuota(RaffleActivityAccountMonth build);

    void insertActivityAccountMonth(RaffleActivityAccountMonth raffleActivityAccountMonth);

    void addAccountQuota(RaffleActivityAccountMonth raffleActivityAccountMonth);

}
