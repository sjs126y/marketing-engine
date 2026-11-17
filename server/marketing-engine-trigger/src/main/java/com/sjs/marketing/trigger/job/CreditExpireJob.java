package com.sjs.marketing.trigger.job;

import com.sjs.marketing.domain.credit.service.CreditExpireWindow;
import com.sjs.marketing.domain.credit.service.ICreditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Date;

/**
 * 权益过期回收任务。
 *
 * <p>每小时整点跑一次。扫描不是「全表找过期的」，而是<b>按自然日分桶</b>：
 * 只扫「今天 + 回看 N 天」这几段 {@code [from, to)}，每段走
 * {@code idx_state_expire(state, expires_at)}，回表行数被限制在一天的量级。</p>
 *
 * <p>回看 N 天是为了补跑：进程停机或任务失败时，那几天的到期批次不会漏掉；
 * 比回看窗口更早的遗留再走一次全量兜底，所以任意时刻宕机都不会有「永久漏回收」。</p>
 *
 * <p>回收动作本身靠 {@code update ... where state='active'} 条件更新抢到才动钱，
 * 多实例同时跑也只会有一个成功，不会重复扣账户。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Component
public class CreditExpireJob {

    @Value("${app.credit.expire-job.enabled:true}")
    private boolean enabled;

    /** 回看天数：每小时扫「今天 + 最近 N 天」 */
    @Value("${app.credit.expire-job.lookback-days:2}")
    private int lookbackDays;

    /** 单次查询的批次数 */
    @Value("${app.credit.expire-job.batch-size:200}")
    private int batchSize;

    /** 单轮最多回收的批次数：防止一次任务把数据库拖住 */
    @Value("${app.credit.expire-job.max-batches-per-run:2000}")
    private int maxBatchesPerRun;

    @Resource
    private ICreditService creditService;

    @Scheduled(cron = "0 0 * * * ?")
    public void exec() {
        if (!enabled) {
            return;
        }
        long begin = System.currentTimeMillis();
        int total = 0;
        try {
            for (CreditExpireWindow.Slot slot : CreditExpireWindow.slots(new Date(), lookbackDays)) {
                if (total >= maxBatchesPerRun) {
                    break;
                }
                total += drain(slot, maxBatchesPerRun - total);
            }
            // 兜底：比回看窗口更早的遗留（长时间停机、手工改数）一次补齐
            if (total < maxBatchesPerRun) {
                total += drainAll(maxBatchesPerRun - total);
            }
            if (total > 0) {
                log.info("权益过期回收完成，本次回收 {} 个批次，耗时 {}ms", total, System.currentTimeMillis() - begin);
            }
        } catch (Exception e) {
            // 调度线程里抛异常会静默终止后续调度，这里必须兜住
            log.error("权益过期回收任务异常", e);
        }
    }

    /** 把一段窗口内的到期批次全部回收（一次查不完就继续查，直到查空） */
    private int drain(CreditExpireWindow.Slot slot, int budget) {
        if (slot.isEmpty() || budget <= 0) {
            return 0;
        }
        int reclaimed = 0;
        while (reclaimed < budget) {
            int take = Math.min(batchSize, budget - reclaimed);
            int count = creditService.expireDueBetween(slot.from(), slot.to(), take);
            if (count <= 0) {
                break;
            }
            reclaimed += count;
        }
        return reclaimed;
    }

    /** 兜底扫描：{@code expires_at <= now} 的全部遗留批次 */
    private int drainAll(int budget) {
        int reclaimed = 0;
        while (reclaimed < budget) {
            int count = creditService.expireDue(Math.min(batchSize, budget - reclaimed));
            if (count <= 0) {
                break;
            }
            reclaimed += count;
        }
        return reclaimed;
    }
}
