package com.sjs.marketing.domain.pay.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 支付订单。
 *
 * <p>状态机刻意做小：{@code created → paid → granted}，另有 {@code closed}（超时未支付）与 {@code refunded}。
 * 「支付」是外部事实（钱已经收了），「发放」是内部动作（权益到账），两步分开，
 * 中间用 outbox 隔开——支付回调成功但发放失败时，钱不会丢，权益也会重试到账。</p>
 *
 * @author marketing-engine contributors
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayOrder {

    /** 待支付 */
    public static final String STATE_CREATED = "created";
    /** 已支付（待发放） */
    public static final String STATE_PAID = "paid";
    /** 已发放（权益到账） */
    public static final String STATE_GRANTED = "granted";
    /** 已关闭（超时未支付） */
    public static final String STATE_CLOSED = "closed";

    /** 订单号：由上游业务单号充当幂等键 */
    private String orderNo;
    /** 用户ID（分片键） */
    private String userId;
    /** 商品编码 */
    private String sku;
    /** 商品类型：credit / quota */
    private String productType;
    /** 发放数量 */
    private Integer amount;
    /** 应付金额（分） */
    private Integer amountCent;
    /** 状态 */
    private String state;
    /** 支付平台侧单号（回调幂等键） */
    private String outTradeNo;
    /** 支付时间 */
    private Date paidAt;
    /** 发放时间 */
    private Date grantedAt;
    /** 关闭时间 */
    private Date closedAt;
    /** 创建时间 */
    private Date createTime;
    /** 更新时间 */
    private Date updateTime;
}
