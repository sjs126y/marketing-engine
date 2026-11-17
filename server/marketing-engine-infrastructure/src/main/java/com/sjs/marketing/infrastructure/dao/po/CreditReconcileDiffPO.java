package com.sjs.marketing.infrastructure.dao.po;

import lombok.Data;

import java.util.Date;

/**
 * 对账差异表（credit_reconcile_diff）记录。
 *
 * @author marketing-engine contributors
 */
@Data
public class CreditReconcileDiffPO {

    private Long id;
    private String runId;
    private String userId;
    private Integer accountBalance;
    private Integer batchRemaining;
    private Integer ledgerSum;
    private Integer diff;
    private String reason;
    private String state;
    private Date createTime;
}
