package com.sjs.marketing.infrastructure.dao.po;

import lombok.Data;

import java.util.Date;

/**
 * 权益流水表（credit_ledger）记录。
 *
 * @author marketing-engine contributors
 */
@Data
public class CreditLedgerPO {

    private Long id;
    private String userId;
    private String bizNo;
    private Long batchId;
    private String scene;
    private String type;
    private Integer delta;
    private Integer balanceAfter;
    private Date createTime;
}
