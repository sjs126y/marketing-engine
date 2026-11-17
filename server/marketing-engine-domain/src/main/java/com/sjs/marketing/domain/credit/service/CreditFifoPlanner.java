package com.sjs.marketing.domain.credit.service;

import com.sjs.marketing.domain.credit.model.CreditBatch;

import java.util.ArrayList;
import java.util.List;

/**
 * FIFO 消耗计划器（纯函数，不依赖 Spring / DB，可直接单测）。
 *
 * <p>规则：按 {@code expiresAt} 升序扣减——先花掉快过期的批次，用户利益最大化。
 * 输入批次列表与本次消耗量，输出「每个批次扣多少」。
 * 计划总和小于消耗量时代表余额不足，由调用方回滚。</p>
 *
 * @author marketing-engine contributors
 */
public final class CreditFifoPlanner {

    private CreditFifoPlanner() {
    }

    /**
     * 一次扣减动作。
     *
     * @param batchId        批次ID
     * @param take           本批次扣减量
     * @param remainingAfter 扣减后该批次剩余
     */
    public record Deduction(long batchId, int take, int remainingAfter) {
    }

    /**
     * 生成扣减计划。
     *
     * @param batches 可用批次（方法内部会再按过期时间排序，防御调用方顺序不对）
     * @param amount  需要消耗的数量
     * @return 扣减计划；总和小于 amount 表示额度不足
     */
    public static List<Deduction> plan(List<CreditBatch> batches, int amount) {
        List<Deduction> deductions = new ArrayList<>();
        if (batches == null || batches.isEmpty() || amount <= 0) {
            return deductions;
        }

        List<CreditBatch> sorted = new ArrayList<>(batches);
        sorted.sort((a, b) -> {
            if (a.getExpiresAt() == null || b.getExpiresAt() == null) {
                return 0;
            }
            int cmp = a.getExpiresAt().compareTo(b.getExpiresAt());
            return cmp != 0 ? cmp : Long.compare(a.getId() == null ? 0 : a.getId(), b.getId() == null ? 0 : b.getId());
        });

        int remaining = amount;
        for (CreditBatch batch : sorted) {
            if (remaining <= 0) {
                break;
            }
            int available = batch.getRemaining() == null ? 0 : batch.getRemaining();
            if (available <= 0) {
                continue;
            }
            int take = Math.min(available, remaining);
            deductions.add(new Deduction(batch.getId(), take, available - take));
            remaining -= take;
        }
        return deductions;
    }

    /** 计划是否够覆盖本次消耗 */
    public static boolean sufficient(List<Deduction> deductions, int amount) {
        int sum = 0;
        for (Deduction d : deductions) {
            sum += d.take();
        }
        return sum >= amount;
    }
}
