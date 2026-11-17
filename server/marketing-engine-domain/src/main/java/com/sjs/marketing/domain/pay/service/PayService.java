package com.sjs.marketing.domain.pay.service;

import com.sjs.marketing.domain.credit.service.ICreditService;
import com.sjs.marketing.domain.pay.adapter.repository.IPayRepository;
import com.sjs.marketing.domain.pay.event.PayGrantEvent;
import com.sjs.marketing.domain.pay.model.PayOrder;
import com.sjs.marketing.domain.pay.model.PayProduct;
import com.sjs.marketing.types.enums.ResponseCode;
import com.sjs.marketing.types.event.BaseEvent;
import com.sjs.marketing.types.exception.AppException;
import com.sjs.marketing.types.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 支付服务实现。
 *
 * <p>三条不变量：</p>
 * <ol>
 *   <li><b>下单幂等</b>：{@code order_no} 唯一，重复下单返回原单；</li>
 *   <li><b>回调幂等</b>：{@code pay_callback_log.out_trade_no} 唯一，重复通知只处理一次；</li>
 *   <li><b>发放幂等</b>：权益发放用 {@code eventId=pay:{orderNo}}，重投不会重复加。</li>
 * </ol>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Service
public class PayService implements IPayService {

    @Resource
    private IPayRepository payRepository;

    @Resource
    private ICreditService creditService;

    @Resource
    private PayGrantEvent payGrantEvent;

    /** 发放时使用的权益场景 */
    private static final String SCENE_PAY = "pay";

    /** 订单超时分钟数（默认 15 分钟） */
    @Value("${app.pay.order-timeout-minutes:15}")
    private int orderTimeoutMinutes;

    @Override
    public PayOrder createOrder(String userId, String sku, String orderNo) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(orderNo)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId / orderNo 不能为空");
        }
        PayProduct product = PayProduct.of(sku);
        if (product == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "未登记的商品：" + sku);
        }

        // 幂等：同一下游单号重复下单，返回原单（金额/商品不一致说明上游传错了，直接拒绝）
        PayOrder existing = payRepository.queryOrderByNo(orderNo);
        if (existing != null) {
            if (!existing.getUserId().equals(userId) || !existing.getSku().equals(product.getSku())) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "订单号已存在且参数不一致：" + orderNo);
            }
            log.info("支付下单幂等命中 orderNo:{} state:{}", orderNo, existing.getState());
            return existing;
        }

        PayOrder order = PayOrder.builder()
                .orderNo(orderNo)
                .userId(userId)
                .sku(product.getSku())
                .productType(product.getType())
                .amount(product.getAmount())
                .amountCent(product.getAmountCent())
                .state(PayOrder.STATE_CREATED)
                .createTime(new Date())
                .build();

        if (!payRepository.insertOrder(order)) {
            // 并发下单：唯一键兜底，读回已存在的那张
            PayOrder concurrent = payRepository.queryOrderByNo(orderNo);
            if (concurrent == null) {
                throw new AppException(ResponseCode.UN_ERROR.getCode(), "下单失败：" + orderNo);
            }
            return concurrent;
        }
        log.info("支付下单成功 orderNo:{} userId:{} sku:{} amountCent:{}", orderNo, userId, sku, product.getAmountCent());
        return order;
    }

    @Override
    public CallbackResult handleCallback(String outTradeNo, String orderNo, int amountCent, int notifySeq, String raw) {
        if (StringUtils.isBlank(outTradeNo) || StringUtils.isBlank(orderNo)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "outTradeNo / orderNo 不能为空");
        }

        PayOrder order = payRepository.queryOrderByNo(orderNo);
        if (order == null) {
            return new CallbackResult(false, false, null, "订单不存在");
        }
        if (order.getAmountCent() == null || order.getAmountCent() != amountCent) {
            // 金额对不上：记录 + 拒绝，绝不能按回调金额发权益
            payRepository.insertCallbackLog(order.getUserId(), orderNo, outTradeNo, notifySeq, raw, "AMOUNT_MISMATCH");
            log.error("支付回调金额不一致 orderNo:{} 订单:{} 回调:{}", orderNo, order.getAmountCent(), amountCent);
            return new CallbackResult(false, false, order.getState(), "金额不一致");
        }
        if (PayOrder.STATE_CLOSED.equals(order.getState())) {
            payRepository.insertCallbackLog(order.getUserId(), orderNo, outTradeNo, notifySeq, raw, "CLOSED");
            return new CallbackResult(false, false, order.getState(), "订单已关闭");
        }

        // 订单已经付过：换一个 out_trade_no 也能认出来，回调日志记 DUPLICATE 而不是 RECEIVED
        if (PayOrder.STATE_PAID.equals(order.getState()) || PayOrder.STATE_GRANTED.equals(order.getState())) {
            payRepository.insertCallbackLog(order.getUserId(), orderNo, outTradeNo, notifySeq, raw, "DUPLICATE");
            log.info("支付回调重复（订单已处理） orderNo:{} state:{}", orderNo, order.getState());
            return new CallbackResult(true, true, order.getState(), "订单已处理");
        }

        // 回调幂等：同一个 out_trade_no 只处理一次
        if (!payRepository.insertCallbackLog(order.getUserId(), orderNo, outTradeNo, notifySeq, raw, "RECEIVED")) {
            log.info("支付回调重复，直接返回成功 orderNo:{} outTradeNo:{} notifySeq:{}", orderNo, outTradeNo, notifySeq);
            return new CallbackResult(true, true, order.getState(), "重复回调");
        }

        PayGrantEvent.PayGrantMessage data = PayGrantEvent.PayGrantMessage.builder()
                .orderNo(orderNo)
                .userId(order.getUserId())
                .sku(order.getSku())
                .amount(order.getAmount())
                .build();
        BaseEvent.EventMessage<PayGrantEvent.PayGrantMessage> message = payGrantEvent.buildEventMessage(data);
        String payload = JsonUtils.toJson(message);

        boolean paid = payRepository.markPaidAndEnqueue(order, outTradeNo,
                payGrantEvent.topic(), message.getId(), payload);
        if (!paid) {
            // 订单已是 paid/granted：属于重复通知，直接当成功
            log.info("支付回调重复（订单已处理） orderNo:{} state:{}", orderNo, order.getState());
            return new CallbackResult(true, true, order.getState(), "订单已处理");
        }

        log.info("支付成功，已写发放消息 orderNo:{} userId:{} amount:{} messageId:{}",
                orderNo, order.getUserId(), order.getAmount(), message.getId());
        return new CallbackResult(true, false, PayOrder.STATE_PAID, "ok");
    }

    @Override
    public boolean grantPaidOrder(String orderNo, String userId) {
        PayOrder order = StringUtils.isBlank(userId)
                ? payRepository.queryOrderByNo(orderNo)
                : payRepository.queryOrder(orderNo, userId);
        if (order == null) {
            log.error("发放权益失败，订单不存在 orderNo:{}", orderNo);
            return false;
        }
        if (PayOrder.STATE_GRANTED.equals(order.getState())) {
            return true;
        }
        if (PayOrder.STATE_CLOSED.equals(order.getState())) {
            log.error("订单已关闭却收到发放消息 orderNo:{}", orderNo);
            return false;
        }

        // 发放幂等：eventId = pay:{orderNo}，重投不会重复加
        boolean granted = creditService.grant(order.getUserId(), SCENE_PAY, order.getAmount(),
                "pay:" + orderNo, null);
        if (!granted) {
            log.info("支付发放幂等命中（已入账） orderNo:{}", orderNo);
        }
        payRepository.markGranted(orderNo, order.getUserId());
        return true;
    }

    @Override
    public PayOrder queryOrder(String orderNo, String userId) {
        return payRepository.queryOrder(orderNo, userId);
    }

    @Override
    public int closeTimeoutOrders(int timeoutMinutes, int limit) {
        int minutes = timeoutMinutes <= 0 ? orderTimeoutMinutes : timeoutMinutes;
        int size = limit <= 0 || limit > 500 ? 200 : limit;
        // 负数：忽略创建时间，把所有 created 订单一律关掉（只给联调/演示用）
        Date deadline = timeoutMinutes < 0
                ? new Date()
                : new Date(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(minutes));
        List<PayOrder> orders = payRepository.queryTimeoutOrders(deadline, size);
        int closed = 0;
        for (PayOrder order : orders) {
            if (payRepository.markClosed(order.getOrderNo(), order.getUserId())) {
                closed++;
            }
        }
        if (closed > 0) {
            log.info("支付订单超时关闭，本次关闭 {} 单（超时 {} 分钟）", closed, minutes);
        }
        return closed;
    }
}
