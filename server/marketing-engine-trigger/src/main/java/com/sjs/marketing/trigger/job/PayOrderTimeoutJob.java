package com.sjs.marketing.trigger.job;

import com.sjs.marketing.domain.pay.service.IPayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 支付订单超时关闭任务。
 *
 * <p>每 5 分钟扫一次「创建超过 15 分钟仍是 created」的订单，条件是
 * {@code update ... where state='created'}，和支付回调抢同一把状态锁：
 * 谁先改成 paid 或 closed，另一条路自然失败。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Component
public class PayOrderTimeoutJob {

    @Value("${app.pay.order-timeout-job.enabled:true}")
    private boolean enabled;

    @Value("${app.pay.order-timeout-minutes:15}")
    private int timeoutMinutes;

    @Resource
    private IPayService payService;

    @Scheduled(cron = "0 */5 * * * ?")
    public void exec() {
        if (!enabled) {
            return;
        }
        try {
            int closed = payService.closeTimeoutOrders(timeoutMinutes, 200);
            if (closed > 0) {
                log.info("支付订单超时关闭完成，本次关闭 {} 单", closed);
            }
        } catch (Exception e) {
            log.error("支付订单超时关闭任务异常", e);
        }
    }
}
