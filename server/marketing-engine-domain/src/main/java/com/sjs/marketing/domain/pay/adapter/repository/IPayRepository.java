package com.sjs.marketing.domain.pay.adapter.repository;

import com.sjs.marketing.domain.pay.model.PayOrder;

import java.util.Date;
import java.util.List;

/**
 * 支付订单仓储。
 *
 * <p>约定：所有写操作都用条件更新表达「状态只能往前走」，
 * 并发的重复回调抢不到就自然变成幂等。</p>
 *
 * @author marketing-engine contributors
 */
public interface IPayRepository {

    /** 订单号是否已存在（下单幂等） */
    PayOrder queryOrderByNo(String orderNo);

    /** 按订单号 + 用户查单（带分片键，精准路由） */
    PayOrder queryOrder(String orderNo, String userId);

    /** 新建订单；order_no 唯一键冲突返回 false */
    boolean insertOrder(PayOrder order);

    /**
     * 记一条回调原始报文。
     *
     * @return false = {@code out_trade_no} 已存在（重复回调）
     */
    boolean insertCallbackLog(String userId, String orderNo, String outTradeNo, int notifySeq, String raw, String result);

    /**
     * 支付成功：条件更新 {@code created → paid}，并在同一事务里写 outbox。
     *
     * <p>两件事必须一起成功：订单变 paid 但消息没写，钱收了权益不到账；
     * 消息写了但订单没变，会重复发放。因为都在同一个库同一个事务，可以做到。</p>
     *
     * @return true = 本次抢到了状态流转（首次回调）；false = 已被处理过
     */
    boolean markPaidAndEnqueue(PayOrder order, String outTradeNo,
                               String topic, String messageId, String payload);

    /** 发放完成：{@code paid → granted} */
    boolean markGranted(String orderNo, String userId);

    /** 关闭订单：{@code created → closed} */
    boolean markClosed(String orderNo, String userId);

    /** 超时未支付的订单（不带分片键，广播各分片） */
    List<PayOrder> queryTimeoutOrders(Date deadline, int limit);
}
