package com.sjs.marketing.domain.credit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对账结果：同一个用户的账，三个来源必须一致。
 *
 * <ul>
 *   <li>{@code accountBalance}：账户表里的余额（冗余汇总）</li>
 *   <li>{@code batchRemaining}：未过期批次剩余之和</li>
 *   <li>{@code ledgerSum}：流水累计（发放 − 消耗 − 过期）</li>
 * </ul>
 *
 * <p>三者任一不等，说明某一步的事务被破坏或有人手工改了数据，
 * 对账任务负责把它暴露出来，而不是静默修正。</p>
 *
 * @author marketing-engine contributors
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditReconcileVO {

    /** 用户ID */
    private String userId;
    /** 账户余额 */
    private Integer accountBalance;
    /** 可用批次剩余之和 */
    private Integer batchRemaining;
    /** 流水累计 */
    private Integer ledgerSum;
    /** 是否一致 */
    private Boolean consistent;
    /** 账户与批次的偏差（账户 − 批次） */
    private Integer diff;

    public static CreditReconcileVO of(String userId, int accountBalance, int batchRemaining, int ledgerSum) {
        boolean consistent = accountBalance == batchRemaining && accountBalance == ledgerSum;
        return CreditReconcileVO.builder()
                .userId(userId)
                .accountBalance(accountBalance)
                .batchRemaining(batchRemaining)
                .ledgerSum(ledgerSum)
                .consistent(consistent)
                .diff(accountBalance - batchRemaining)
                .build();
    }
}
