package com.sjs.marketing.domain.credit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 权益账户（积分 / 次数共用一张表，按 scene 区分语义）。
 *
 * <p>账户只存汇总值，明细在 {@link CreditBatch}（发放批次）与 {@link CreditLedger}（流水）。
 * 余额 = 各 active 批次 remaining 之和，账户里的 balance 是它的冗余汇总，
 * 供高频查询使用，由事务保证两者同步。</p>
 *
 * @author marketing-engine contributors
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditAccount {

    /** 用户ID */
    private String userId;
    /** 可用余额 */
    private Integer balance;
    /** 冻结数量 */
    private Integer frozen;
    /** 累计发放 */
    private Integer totalGranted;
    /** 累计消耗 */
    private Integer totalConsumed;
    /** 累计过期回收 */
    private Integer totalExpired;
    /** 更新时间 */
    private Date updateTime;

    /** 空账户（尚未产生任何发放） */
    public static CreditAccount empty(String userId) {
        return CreditAccount.builder()
                .userId(userId)
                .balance(0)
                .frozen(0)
                .totalGranted(0)
                .totalConsumed(0)
                .totalExpired(0)
                .build();
    }
}
