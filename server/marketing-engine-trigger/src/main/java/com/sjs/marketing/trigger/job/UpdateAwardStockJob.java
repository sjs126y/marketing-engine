package com.sjs.marketing.trigger.job;

import com.sjs.marketing.domain.strategy.model.valobj.StrategyAwardStockKeyVO;
import com.sjs.marketing.domain.strategy.service.IRaffleStock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 奖品库存异步落库。
 *
 * <p>抽奖时只在 Redis 扣减并把「已扣减」事件投进队列，这里负责把它们落到
 * {@code strategy_award.award_count_surplus}。</p>
 *
 * <p>原先每 30 秒只取一条：压测下 89 次抽奖只有 2 条在 35 秒内落库，
 * 数据库严重滞后于 Redis。改成每轮循环排空（带上限防止单次任务过久），
 * 周转速度从「30 秒一条」提升到「一轮几百条」。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Component
public class UpdateAwardStockJob {

    /** 单轮最多处理条数，避免一次任务占用线程过久 */
    private static final int MAX_PER_RUN = 500;

    @Resource
    private IRaffleStock raffleStock;

    @Scheduled(cron = "0/5 * * * * ?")
    public void exec() {
        int handled = 0;
        try {
            while (handled < MAX_PER_RUN) {
                StrategyAwardStockKeyVO key = raffleStock.takeQueueValue();
                if (key == null) {
                    break;
                }
                raffleStock.updateStrategyAwardStock(key.getStrategyId(), key.getAwardId(), key.getConsumedAfter());
                handled++;
            }
            if (handled > 0) {
                log.info("更新奖品库存任务结束，本轮处理 {} 条", handled);
            }
        } catch (Exception e) {
            log.error("定时任务，更新奖品消耗库存失败，本轮已处理 {} 条", handled, e);
        }
    }
}
