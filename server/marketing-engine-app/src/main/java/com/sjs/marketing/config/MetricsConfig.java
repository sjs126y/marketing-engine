package com.sjs.marketing.config;

import com.sjs.marketing.domain.outbox.service.IOutboxAckService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;

/**
 * 关键指标注册。
 *
 * <p>目前暴露两类判断「系统是否健康」的指标：</p>
 * <ul>
 *   <li>{@code outbox.pending}：本地消息表待投递条数，持续上涨说明 MQ 链路卡住；</li>
 *   <li>Actuator 自带的 JVM / HTTP 指标，够定位大多数问题。</li>
 * </ul>
 *
 * <p>抓取地址：{@code GET /actuator/prometheus}。</p>
 *
 * @author marketing-engine contributors
 */
@Configuration
public class MetricsConfig {

    @Resource
    private MeterRegistry meterRegistry;

    @Resource
    private IOutboxAckService outboxAckService;

    @PostConstruct
    public void registerOutboxGauge() {
        Gauge.builder("outbox.pending", outboxAckService, IOutboxAckService::pendingCount)
                .description("本地消息表待投递消息数")
                .tag("application", "marketing-engine")
                .register(meterRegistry);
    }

}
