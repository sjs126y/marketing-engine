package com.sjs.trigger.job;

import com.sjs.domain.strategy.model.valobj.StrategyAwardStockKeyVO;
import com.sjs.domain.strategy.service.IRaffleStock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class UpdateAwardStockJob {

    @Resource
    private IRaffleStock raffleStock;

    @Scheduled(cron = "0/5 * * * * ?")
    public void exec(){
        try{
            log.info("更新奖品库存任务开始");
            StrategyAwardStockKeyVO strategyAwardStockKeyVO = raffleStock.takeQueueValue();
            if(null == strategyAwardStockKeyVO) return;
            raffleStock.updateStrategyAwardStock(strategyAwardStockKeyVO.getStrategyId(), strategyAwardStockKeyVO.getAwardId());
            log.info("更新奖品库存任务结束, strategyAwardStockKeyVO : {}", strategyAwardStockKeyVO);
        } catch (Exception e) {
            log.error("定时任务，更新奖品消耗库存失败", e);
        }
    }
}
