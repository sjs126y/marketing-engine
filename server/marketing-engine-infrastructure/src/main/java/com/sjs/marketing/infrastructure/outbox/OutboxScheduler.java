package com.sjs.marketing.infrastructure.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * outbox 的两个定时任务：
 * <ul>
 *   <li>投递：每秒扫一批 pending 发给 MQ；</li>
 *   <li>恢复：每分钟处理卡住的 sending 和超时未回执的 sent。</li>
 * </ul>
 *
 * <p>分库分表由 ShardingSphere 负责：outbox 按 user_id 分库，
 * 这两个查询不带分片键，会广播到所有分片并归并结果。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Component
public class OutboxScheduler {

    @Resource
    private OutboxService outboxService;

    @Scheduled(fixedDelay = 1000L)
    public void dispatch() {
        try {
            outboxService.dispatchOnce();
        } catch (Exception e) {
            log.error("outbox 投递任务异常", e);
        }
    }

    @Scheduled(fixedDelay = 60000L)
    public void recover() {
        try {
            outboxService.recover();
        } catch (Exception e) {
            log.error("outbox 恢复任务异常", e);
        }
    }

}
