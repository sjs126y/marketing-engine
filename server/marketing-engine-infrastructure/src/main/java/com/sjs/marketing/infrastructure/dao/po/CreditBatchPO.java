package com.sjs.marketing.infrastructure.dao.po;

import lombok.Data;

import java.util.Date;

/**
 * 权益批次表（credit_batch）记录。
 *
 * @author marketing-engine contributors
 */
@Data
public class CreditBatchPO {

    private Long id;
    private String userId;
    private String eventId;
    private String scene;
    private Integer amount;
    private Integer remaining;
    private Date grantedAt;
    private Date expiresAt;
    private String state;
}
