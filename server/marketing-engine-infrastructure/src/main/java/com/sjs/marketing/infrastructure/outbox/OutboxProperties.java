package com.sjs.marketing.infrastructure.outbox;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * outbox 调度参数。
 *
 * @author marketing-engine contributors
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.outbox")
public class OutboxProperties {

    /** 单次扫描条数 */
    private int batchSize = 200;

    /** 最大重试次数，超过进入 dead */
    private int maxRetry = 8;

    /** sending 状态超过该秒数视为进程崩溃，退回 pending */
    private int stuckSeconds = 120;

    /** sent 后超过该秒数仍未收到消费回执，重新投递（at-least-once） */
    private int consumeTimeoutSeconds = 300;

    /** 退避上限（秒） */
    private int maxBackoffSeconds = 300;

}
