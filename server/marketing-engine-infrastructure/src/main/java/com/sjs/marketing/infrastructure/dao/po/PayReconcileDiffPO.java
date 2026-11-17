package com.sjs.marketing.infrastructure.dao.po;

import lombok.Data;

import java.util.Date;

/**
 * 支付三方对账差异表（pay_reconcile_diff）记录。
 *
 * @author marketing-engine contributors
 */
@Data
public class PayReconcileDiffPO {

    private Long id;
    private String runId;
    private String orderNo;
    private String userId;
    private String orderState;
    private Integer orderAmount;
    private Integer ledgerAmount;
    private Integer refundedCent;
    private String reason;
    private String state;
    private Date createTime;
}
