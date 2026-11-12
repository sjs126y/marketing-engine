package com.sjs.marketing.infrastructure.dao;
import com.sjs.marketing.infrastructure.dao.po.RaffleActivityAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author marketing-engine contributors
 * @description 抽奖活动账户表
 * @create 2024-03-09 10:05
 */
@Mapper
public interface IRaffleActivityAccountDao {
    int updateAccountQuota(RaffleActivityAccount raffleActivityAccount);

    void insert(RaffleActivityAccount raffleActivityAccount);
    RaffleActivityAccount queryActivityAccountByUserId(RaffleActivityAccount raffleActivityAccountReq);

    int updateActivityAccountSubtractionQuota(RaffleActivityAccount build);

    void updateActivityAccountMonthSurplusImageQuota(RaffleActivityAccount raffleActivityAccountReq);

    void updateActivityAccountDaySurplusImageQuota(RaffleActivityAccount build);
}
