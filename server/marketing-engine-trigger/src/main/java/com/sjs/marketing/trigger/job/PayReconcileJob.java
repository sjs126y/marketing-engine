package com.sjs.marketing.trigger.job;

import com.sjs.marketing.domain.pay.model.PayReconcileSummary;
import com.sjs.marketing.domain.pay.service.IPayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 支付三方对账任务。
 *
 * <p>每天凌晨跑一次：订单状态、订单应发数量、权益流水净入账三者比对，
 * 不一致的写进 {@code pay_reconcile_diff} 并打 error 日志。</p>
 *
 * <p>和资产对账一样：<b>只暴露、不修正</b>。支付链路涉及真金白银，
 * 自动改数比不改更危险。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Component
public class PayReconcileJob {

    @Value("${app.pay.reconcile-job.enabled:true}")
    private boolean enabled;

    @Value("${app.pay.reconcile-job.limit:200}")
    private int limit;

    @Resource
    private IPayService payService;

    @Scheduled(cron = "0 0 3 * * ?")
    public void exec() {
        if (!enabled) {
            return;
        }
        try {
            PayReconcileSummary summary = payService.reconcilePay(limit);
            if (summary.getInconsistent() != null && summary.getInconsistent() > 0) {
                log.error("支付对账发现偏差 runId:{} 扫描:{} 不一致:{} 已落表:{}",
                        summary.getRunId(), summary.getScanned(), summary.getInconsistent(), summary.getStored());
            } else {
                log.info("支付对账完成，{} 单全部一致 runId:{} 耗时:{}ms",
                        summary.getScanned(), summary.getRunId(), summary.getCostMs());
            }
        } catch (Exception e) {
            log.error("支付对账任务异常", e);
        }
    }
}
