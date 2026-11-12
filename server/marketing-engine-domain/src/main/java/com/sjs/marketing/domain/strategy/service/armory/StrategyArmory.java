package com.sjs.marketing.domain.strategy.service.armory;

import com.sjs.marketing.domain.strategy.adapter.repository.IStrategyRepository;
import com.sjs.marketing.domain.strategy.model.entity.StrategyAwardEntity;
import com.sjs.marketing.domain.strategy.model.entity.StrategyEntity;
import com.sjs.marketing.domain.strategy.model.entity.StrategyRuleEntity;
import com.sjs.marketing.types.common.Constants;
import com.sjs.marketing.types.enums.ResponseCode;
import com.sjs.marketing.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.*;

@Slf4j
@Service
public class StrategyArmory implements IStrategyArmory, IStrategyDispatch {

    @Resource
    private IStrategyRepository repository;

    @Override
    public boolean assembleLotteryStrategyByActivityId(Long activityId) {
        Long strategyId = repository.queryStrategyIdByActivityId(activityId);
        return assembleLotteryStrategy(strategyId);
    }

    @Override
    public boolean assembleLotteryStrategy(Long strategyId) {
        //1.获取策略配置信息
        List<StrategyAwardEntity> strategyAwardEntities = repository.queryStrategyAwardList(strategyId);

        //填充库存
        for (StrategyAwardEntity strategyAwardEntity : strategyAwardEntities) {
            Integer awardId = strategyAwardEntity.getAwardId();
            Integer awardCount = strategyAwardEntity.getAwardCount();
            cacheStrategyAwardCount(strategyId, awardId, awardCount);
        }

        assembleLotteryStrategy(String.valueOf(strategyId), strategyAwardEntities);

        //2.查询策略
        StrategyEntity strategyEntity = repository.queryStrategyEntityByStrategyId(strategyId);
        String ruleWeight = strategyEntity.getRuleWeight();
        if(null == ruleWeight) return true;

        StrategyRuleEntity strategyRuleEntity = repository.queryStrategyRuleEntity(strategyId, ruleWeight);
        // 业务异常，策略规则中 rule_weight 权重规则已适用但未配置
        if (null == strategyRuleEntity) {
            throw new AppException(ResponseCode.STRATEGY_RULE_WEIGHT_IS_NULL.getCode(), ResponseCode.STRATEGY_RULE_WEIGHT_IS_NULL.getInfo());
        }

        Map<String, List<Integer>> ruleWeightValueMap = strategyRuleEntity.getRuleWeightValues();
        if(ruleWeightValueMap == null || ruleWeightValueMap.isEmpty()) return true;

        Set<String> keys = ruleWeightValueMap.keySet();

        for (String key : keys) {
            List<Integer> ruleWeightValues = ruleWeightValueMap.get(key);
            List<StrategyAwardEntity> strategyAwardEntitiesClone = new ArrayList<>(strategyAwardEntities);
            strategyAwardEntitiesClone.removeIf(entity -> !ruleWeightValues.contains(entity.getAwardId()));
            assembleLotteryStrategy(String.valueOf(strategyId).concat("_").concat(key), strategyAwardEntitiesClone);
        }

        return true;
    }

    private void cacheStrategyAwardCount(Long strategyId, Integer awardId, Integer awardCount) {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_KEY + strategyId + Constants.UNDERLINE + awardId;
        repository.cacheStrategyAwardCount(cacheKey, awardCount);
    }

    private void assembleLotteryStrategy(String key, List<StrategyAwardEntity> strategyAwardEntities) {
        Map<Integer, Integer> rateTable = buildRateTable(strategyAwardEntities);
        // 存放到 Redis
        repository.storeStrategyAwardSearchRateTables(key, rateTable.size(), rateTable);
    }

    /**
     * 把奖品概率展开成「下标 → 奖品ID」的区间查找表，并打乱顺序。
     *
     * <p>算法：取最小概率作为最小刻度，区间长度 = 总概率 / 最小概率（向上取整），
     * 每个奖品按 {@code 区间长度 × 概率} 占位。打乱是为了避免同一奖品占据连续下标、
     * 被人通过多次抽样摸出规律。</p>
     *
     * <p>前提是各奖品概率之和为 1：归一后每个奖品的占位数正好是 {@code 概率 / 最小概率}，
     * 总占位数刚好填满区间；否则向上取整会把分布抹平（见 {@code StrategyConfigValidator}）。</p>
     *
     * @param strategyAwardEntities 参与本次抽奖的奖品（权重档位传入的是子集）
     * @return 下标 → 奖品ID 的查找表，已乱序
     */
    public static Map<Integer, Integer> buildRateTable(List<StrategyAwardEntity> strategyAwardEntities) {
        // 1. 获取最小概率值
        BigDecimal minAwardRate = strategyAwardEntities.stream()
                .map(StrategyAwardEntity::getAwardRate)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        // 2. 获取概率值总和
        BigDecimal totalAwardRate = strategyAwardEntities.stream()
                .map(StrategyAwardEntity::getAwardRate)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. 得到概率范围的刻度数
        BigDecimal rateRange = totalAwardRate.divide(minAwardRate, 0, RoundingMode.CEILING);

        // 4. 按概率占位填充
        List<Integer> strategyAwardSearchRateTables = new ArrayList<>(rateRange.intValue());
        for (StrategyAwardEntity strategyAward : strategyAwardEntities) {
            Integer awardId = strategyAward.getAwardId();
            BigDecimal awardRate = strategyAward.getAwardRate();
            for (int i = 0; i < rateRange.multiply(awardRate).setScale(0, RoundingMode.CEILING).intValue(); i++) {
                strategyAwardSearchRateTables.add(awardId);
            }
        }

        // 5. 乱序，避免同一奖品的槽位连续
        Collections.shuffle(strategyAwardSearchRateTables);

        // 6. 下标 → 奖品ID
        Map<Integer, Integer> shuffleStrategyAwardSearchRateTable = new LinkedHashMap<>();
        for (int i = 0; i < strategyAwardSearchRateTables.size(); i++) {
            shuffleStrategyAwardSearchRateTable.put(i, strategyAwardSearchRateTables.get(i));
        }
        return shuffleStrategyAwardSearchRateTable;
    }

    @Override
    public Integer getRandomAwardId(Long strategyId) {
        //1.先获取总数范围
        int rateRange = repository.getRateRange(strategyId);

        //2.在获取随机奖品
        return repository.getStrategyAwardAssemble(String.valueOf(strategyId), new SecureRandom().nextInt(rateRange));
    }
    @Override
    public Integer getRandomAwardId(Long strategyId, String ruleWeightValue) {
        String key = String.valueOf(strategyId).concat("_").concat(ruleWeightValue);
        // 分布式部署下，不一定为当前应用做的策略装配。也就是值不一定会保存到本应用，而是分布式应用，所以需要从 Redis 中获取。
        int rateRange = repository.getRateRange(key);
        // 通过生成的随机值，获取概率值奖品查找表的结果
        return repository.getStrategyAwardAssemble(key, new SecureRandom().nextInt(rateRange));
    }

    @Override
    public Integer subtractionAwardStock(Long strategyId, Integer awardId, Date endDateTime) {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_KEY + strategyId + Constants.UNDERLINE + awardId;
        return repository.subtractionAwardStock(cacheKey, endDateTime);
    }
}
