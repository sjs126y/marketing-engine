package com.sjs.marketing.domain.credit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一次全量对账的汇总结果。
 *
 * @author marketing-engine contributors
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditReconcileSummary {

    /** 对账批次号 */
    private String runId;
    /** 扫描的账户数 */
    private Integer scanned;
    /** 一致的用户数 */
    private Integer consistent;
    /** 不一致的用户数 */
    private Integer inconsistent;
    /** 落表的差异条数 */
    private Integer stored;
    /** 耗时（毫秒） */
    private Long costMs;
}
