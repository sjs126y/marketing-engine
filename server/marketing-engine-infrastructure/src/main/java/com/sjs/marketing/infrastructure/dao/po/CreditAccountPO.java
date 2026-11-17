package com.sjs.marketing.infrastructure.dao.po;

import lombok.Data;

import java.util.Date;

/**
 * 权益账户表（credit_account）记录。
 *
 * @author marketing-engine contributors
 */
@Data
public class CreditAccountPO {

    private Long id;
    private String userId;
    private Integer balance;
    private Integer frozen;
    private Integer totalGranted;
    private Integer totalConsumed;
    private Integer totalExpired;
    private Date createTime;
    private Date updateTime;
}
