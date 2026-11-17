package com.sjs.marketing.domain.pay.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一次支付三方对账的汇总。
 *
 * @author marketing-engine contributors
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayReconcileSummary {

    /** 对账批次号 */
    private String runId;
    /** 扫描订单数 */
    private Integer scanned;
    /** 一致订单数 */
    private Integer consistent;
    /** 不一致订单数 */
    private Integer inconsistent;
    /** 落表差异条数 */
    private Integer stored;
    /** 耗时（毫秒） */
    private Long costMs;
}
