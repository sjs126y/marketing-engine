package com.sjs.marketing.infrastructure.dao;

import com.sjs.marketing.infrastructure.dao.po.StrategyAward;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @author marketing-engine contributors
 * @description 抽奖策略奖品明细配置 - 概率、规则 DAO
 * @create 2023-12-16 13:24
 */
@Mapper
public interface IStrategyAwardDao {

    List<StrategyAward> queryStrategyAwardList();

    List<StrategyAward> queryStrategyAwardListByStrategyId(Long strategyId);

    String queryStrategyAwardRuleModels(StrategyAward strategyAwardReq);

    /**
     * 以绝对量回写奖品库存：{@code 总量 - 已扣数}，只有更大的已扣数才生效（单调保护）。
     */
    int updateAppliedConsumed(@org.apache.ibatis.annotations.Param("strategyId") Long strategyId,
                              @org.apache.ibatis.annotations.Param("awardId") Integer awardId,
                              @org.apache.ibatis.annotations.Param("consumedAfter") Integer consumedAfter);

    StrategyAward queryStrategyAward(StrategyAward strategyAwardReq);
}
