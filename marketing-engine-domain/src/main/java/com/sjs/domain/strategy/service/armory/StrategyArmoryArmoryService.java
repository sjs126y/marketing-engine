package com.sjs.domain.strategy.service.armory;

import com.sjs.domain.strategy.adapter.repository.IStrategyRepository;
import com.sjs.domain.strategy.model.entity.StrategyAwardEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.*;

@Slf4j
@Service
public class StrategyArmoryArmoryService implements IStrategyArmoryService {

    @Resource
    private IStrategyRepository strategyRepository;

    @Override
    public boolean assembleLotteryStrategy(Long strategyId) {
        //1.获取策略配置信息
        List<StrategyAwardEntity> strategyAwardEntities = strategyRepository.queryStrategyAwardList(strategyId);

        //2.获取最小概率
        BigDecimal minAwardRate = strategyAwardEntities.stream()
                                                       .map(StrategyAwardEntity::getAwardRate)
                                                       .min(BigDecimal::compareTo)
                                                       .orElse(BigDecimal.ZERO);

        //3.获取概率总和
        BigDecimal totalAwardRate = strategyAwardEntities.stream()
                                                     .map(StrategyAwardEntity::getAwardRate)
                                                     .reduce(BigDecimal.ZERO, BigDecimal::add);

        //4.获取概率划分范围
        BigDecimal rateRange = totalAwardRate.divide(minAwardRate, 0, RoundingMode.CEILING);

        //5.获取奖品概率查找表
        List<Integer> strategyAwardSearchRateTables = new ArrayList<>(rateRange.intValue());
        for (StrategyAwardEntity strategyAwardEntity : strategyAwardEntities) {
            BigDecimal awardRate = strategyAwardEntity.getAwardRate();
            Integer awardId = strategyAwardEntity.getAwardId();

            for(int i = 0; i < awardRate.multiply(rateRange).intValue(); i++){
                strategyAwardSearchRateTables.add(awardId);
            }
        }

        //6.打乱查找表
        Collections.shuffle(strategyAwardSearchRateTables);

        //7.写入奖品概率查找表
        Map<Integer, Integer> strategyAwardSearchRateMap = new HashMap<>();
        for(int i = 0; i < strategyAwardSearchRateTables.size(); i++){
            strategyAwardSearchRateMap.put(i, strategyAwardSearchRateTables.get(i));
        }

        //存放redis
        strategyRepository.storeStrategyAwardSearchRateTables(strategyId, strategyAwardSearchRateMap.size(), strategyAwardSearchRateMap);

        return true;
    }

    @Override
    public Integer getRandomAwardId(Long strategyId) {
        //1.先获取总数范围
        int rateRange = strategyRepository.getRateRange(strategyId);

        //2.在获取随机奖品
        return strategyRepository.getStrategyAwardAssemble(strategyId, new SecureRandom().nextInt(rateRange));
    }
}
