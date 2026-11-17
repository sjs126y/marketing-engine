package com.sjs.marketing.infrastructure.dao.po;

import lombok.Data;

import java.util.Date;

/**
 * 支付订单表（pay_order）记录。
 *
 * @author marketing-engine contributors
 */
@Data
public class PayOrderPO {

    private Long id;
    private String orderNo;
    private String userId;
    private String sku;
    private String productType;
    private Integer amount;
    private Integer amountCent;
    private String state;
    private String outTradeNo;
    private Date paidAt;
    private Date grantedAt;
    private Date closedAt;
    private Date createTime;
    private Date updateTime;
}
