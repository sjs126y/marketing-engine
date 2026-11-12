package com.sjs.marketing.trigger.job;

import com.sjs.marketing.domain.activity.model.valobj.ActivitySkuStockReconcileVO;
import com.sjs.marketing.domain.activity.service.IActivityStockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * SKU 库存对账任务。
 *
 * <p>对比 Redis 与数据库的已扣数。Redis 是扣减的事实来源，数据库是异步回写的结果，
 * 两者出现偏差说明有消息丢失或重复应用——这里只负责发现与告警，
 * 修正动作留给人工或后续的自动补偿，避免「系统自己悄悄改数」。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Component
public class ActivitySkuStockReconcileJob {

    /** 允许的偏差，1 以内视为正常（回写存在秒级延迟） */
    @Value("${app.stock.reconcile-threshold:3}")
    private int threshold;

    @Resource
    private IActivityStockService activityStockService;

    @Scheduled(cron = "0 0/5 * * * ?")
    public void exec() {
        try {
            List<ActivitySkuStockReconcileVO> results = activityStockService.reconcile();
            if (results.isEmpty()) {
                return;
            }

            int inconsistent = 0;
            for (ActivitySkuStockReconcileVO result : results) {
                if (result.inconsistent(threshold)) {
                    inconsistent++;
                    log.warn("SKU 库存对账不一致 sku:{} 总量:{} Redis已扣:{} 数据库已扣:{} 偏差:{}",
                            result.getSku(), result.getStockCount(),
                            result.getRedisConsumed(), result.getDbConsumed(), result.diff());
                }
            }

            if (inconsistent == 0) {
                log.info("SKU 库存对账完成，共 {} 个 SKU，无异常", results.size());
            } else {
                log.error("SKU 库存对账完成，共 {} 个 SKU，{} 个存在偏差", results.size(), inconsistent);
            }
        } catch (Exception e) {
            log.error("SKU 库存对账任务异常", e);
        }
    }
}
