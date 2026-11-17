package com.sjs.marketing.test.unit;

import com.sjs.marketing.domain.credit.model.CreditBatch;
import com.sjs.marketing.domain.credit.service.CreditFifoPlanner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权益消耗的 FIFO 计划验证。
 *
 * <p>不依赖 Spring / 数据库：{@code plan} 是纯函数，输入批次与消耗量、
 * 输出「每个批次扣多少」。核心断言是**顺序**——必须先扣快过期的批次。</p>
 *
 * @author marketing-engine contributors
 */
class CreditFifoPlannerTest {

    @Test
    @DisplayName("按过期时间升序扣减：先花快过期的批次，输入顺序打乱也不影响结果")
    void shouldDeductExpiringFirst() {
        CreditBatch late = batch(2L, 50, "2026-12-31");
        CreditBatch early = batch(1L, 20, "2026-11-20");

        // 故意把晚过期的放前面，验证计划器自己会排序
        List<CreditFifoPlanner.Deduction> plan = CreditFifoPlanner.plan(List.of(late, early), 25);

        assertThat(plan).hasSize(2);
        assertThat(plan.get(0).batchId()).isEqualTo(1L);
        assertThat(plan.get(0).take()).isEqualTo(20);
        assertThat(plan.get(0).remainingAfter()).isZero();
        assertThat(plan.get(1).batchId()).isEqualTo(2L);
        assertThat(plan.get(1).take()).isEqualTo(5);
        assertThat(plan.get(1).remainingAfter()).isEqualTo(45);
        assertThat(CreditFifoPlanner.sufficient(plan, 25)).isTrue();
    }

    @Test
    @DisplayName("批次总量不足时：计划总和小于消耗量，由调用方回滚")
    void shouldReportInsufficientWhenBatchesNotEnough() {
        List<CreditFifoPlanner.Deduction> plan = CreditFifoPlanner.plan(
                List.of(batch(1L, 10, "2026-11-20"), batch(2L, 5, "2026-11-25")), 30);

        assertThat(plan).hasSize(2);
        assertThat(CreditFifoPlanner.sufficient(plan, 30)).isFalse();
    }

    @Test
    @DisplayName("没有可用批次时返回空计划")
    void shouldReturnEmptyPlanWhenNoBatch() {
        assertThat(CreditFifoPlanner.plan(List.of(), 5)).isEmpty();
        assertThat(CreditFifoPlanner.plan(null, 5)).isEmpty();
        assertThat(CreditFifoPlanner.plan(List.of(batch(1L, 10, "2026-11-20")), 0)).isEmpty();
    }

    @Test
    @DisplayName("跳过分片内已扣完（remaining = 0）的批次")
    void shouldSkipExhaustedBatch() {
        List<CreditFifoPlanner.Deduction> plan = CreditFifoPlanner.plan(
                List.of(batch(1L, 0, "2026-11-19"), batch(2L, 8, "2026-11-20")), 8);

        assertThat(plan).hasSize(1);
        assertThat(plan.get(0).batchId()).isEqualTo(2L);
        assertThat(plan.get(0).remainingAfter()).isZero();
    }

    private CreditBatch batch(Long id, int remaining, String expiresAt) {
        return CreditBatch.builder()
                .id(id)
                .userId("u1")
                .amount(remaining)
                .remaining(remaining)
                .state(CreditBatch.STATE_ACTIVE)
                .grantedAt(date("2026-11-01"))
                .expiresAt(date(expiresAt))
                .build();
    }

    private Date date(String isoDate) {
        return Date.from(LocalDateTime.parse(isoDate + "T00:00:00")
                .atZone(ZoneId.systemDefault()).toInstant());
    }
}
