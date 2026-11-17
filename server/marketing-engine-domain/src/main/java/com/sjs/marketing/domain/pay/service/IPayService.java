package com.sjs.marketing.domain.pay.service;

import com.sjs.marketing.domain.pay.model.PayOrder;

/**
 * 支付服务：下单、处理支付回调、发放权益、超时关单。
 *
 * <p>调用方是上游业务系统（开放接口），它负责「谁在买、买什么」，
 * 权益中心负责「钱到了没有、东西发了没有」。</p>
 *
 * @author marketing-engine contributors
 */
public interface IPayService {

    /**
     * 下单（幂等：同一 orderNo 重复调用返回同一张单）。
     *
     * @param userId  用户ID
     * @param sku     商品编码
     * @param orderNo 上游业务单号，作为订单号与幂等键
     */
    PayOrder createOrder(String userId, String sku, String orderNo);

    /**
     * 处理支付平台回调（模拟）。
     *
     * @param outTradeNo 支付平台单号（回调幂等键）
     * @param orderNo    商户订单号
     * @param amountCent 实付金额（分），必须与订单金额一致
     * @param notifySeq  第几次通知（演示重复通知用）
     * @param raw        原始报文，落回调日志留痕
     * @return 处理结果
     */
    CallbackResult handleCallback(String outTradeNo, String orderNo, int amountCent, int notifySeq, String raw);

    /**
     * 发放订单权益（outbox 消息消费入口，幂等）。
     *
     * @return true = 已到账（含幂等命中）
     */
    boolean grantPaidOrder(String orderNo, String userId);

    /** 查询订单 */
    PayOrder queryOrder(String orderNo, String userId);

    /**
     * 关闭超时未支付的订单。
     *
     * @param timeoutMinutes 超时分钟数
     * @param limit          单轮处理上限
     * @return 本次关闭的订单数
     */
    int closeTimeoutOrders(int timeoutMinutes, int limit);

    /**
     * 回调处理结果。
     *
     * @param duplicated 是否重复回调（重复时不再发放，但返回成功）
     * @param state      订单当前状态
     */
    record CallbackResult(boolean accepted, boolean duplicated, String state, String reason) {
    }
}
