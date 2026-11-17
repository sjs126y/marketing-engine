package com.sjs.marketing.trigger.job;

import com.sjs.marketing.domain.credit.model.CreditReconcileSummary;
import com.sjs.marketing.domain.credit.service.ICreditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 权益对账任务。
 *
 * <p>每天凌晨跑一次全量对账，比对三个来源：账户余额、未核销批次剩余之和、流水累计。
 * 不一致的写进 {@code credit_reconcile_diff} 并打 error 日志。</p>
 *
 * <p><b>只暴露、不修正。</b>自动修数会让「记账出了 bug」变成「数据被系统悄悄改过」，
 * 事后查不出任何痕迹；差异表 + 告警至少要保住可追溯性。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Component
public class CreditReconcileJob {

    @Value("${app.credit.reconcile-job.enabled:true}")
    private boolean enabled;

    /** 每页账户数 */
    @Value("${app.credit.reconcile-job.page-size:200}")
    private int pageSize;

    @Resource
    private ICreditService creditService;

    @Scheduled(cron = "0 0 2 * * ?")
    public void exec() {
        if (!enabled) {
            return;
        }
        try {
            CreditReconcileSummary summary = creditService.reconcileAll(pageSize);
            if (summary.getInconsistent() != null && summary.getInconsistent() > 0) {
                log.error("权益对账发现偏差 runId:{} 扫描:{} 不一致:{} 已落表:{}",
                        summary.getRunId(), summary.getScanned(), summary.getInconsistent(), summary.getStored());
            } else {
                log.info("权益对账完成，{} 个账户全部一致 runId:{} 耗时:{}ms",
                        summary.getScanned(), summary.getRunId(), summary.getCostMs());
            }
        } catch (Exception e) {
            log.error("权益对账任务异常", e);
        }
    }
}
