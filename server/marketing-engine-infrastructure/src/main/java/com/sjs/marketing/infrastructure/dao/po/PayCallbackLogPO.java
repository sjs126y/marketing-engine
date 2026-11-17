package com.sjs.marketing.infrastructure.dao.po;

import lombok.Data;

import java.util.Date;

/**
 * 支付回调日志（pay_callback_log）记录。
 *
 * <p>{@code out_trade_no} 唯一：支付平台会重复通知（微信 24 小时内最多 8 次），
 * 这一条既是幂等闸门，也是事后排查「到底通知了几次」的证据。</p>
 *
 * @author marketing-engine contributors
 */
@Data
public class PayCallbackLogPO {

    private Long id;
    private String outTradeNo;
    private String orderNo;
    private String userId;
    private Integer notifySeq;
    private String raw;
    private String result;
    private Date createTime;
}
