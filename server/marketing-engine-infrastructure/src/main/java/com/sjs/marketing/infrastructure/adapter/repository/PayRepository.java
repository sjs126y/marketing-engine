package com.sjs.marketing.infrastructure.adapter.repository;

import com.sjs.marketing.domain.pay.adapter.repository.IPayRepository;
import com.sjs.marketing.domain.pay.model.PayOrder;
import com.sjs.marketing.infrastructure.dao.IPayDao;
import com.sjs.marketing.infrastructure.dao.po.PayCallbackLogPO;
import com.sjs.marketing.infrastructure.dao.po.PayOrderPO;
import com.sjs.marketing.infrastructure.outbox.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 支付订单仓储实现。
 *
 * <p>「订单变 paid」和「发放消息入库」放在同一个本地事务里：
 * 两件事必须一起成功，否则要么钱收了权益不发（漏），要么消息发了订单没变（重）。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Repository
public class PayRepository implements IPayRepository {

    @Resource
    private IPayDao payDao;

    @Resource
    private OutboxService outboxService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public PayOrder queryOrderByNo(String orderNo) {
        return toOrder(payDao.queryOrderByNo(orderNo));
    }

    @Override
    public PayOrder queryOrder(String orderNo, String userId) {
        return toOrder(payDao.queryOrder(orderNo, userId));
    }

    @Override
    public boolean insertOrder(PayOrder order) {
        PayOrderPO po = new PayOrderPO();
        po.setOrderNo(order.getOrderNo());
        po.setUserId(order.getUserId());
        po.setSku(order.getSku());
        po.setProductType(order.getProductType());
        po.setAmount(order.getAmount());
        po.setAmountCent(order.getAmountCent());
        po.setState(order.getState());
        po.setCreateTime(order.getCreateTime());
        try {
            return payDao.insertOrder(po) == 1;
        } catch (DuplicateKeyException e) {
            log.info("支付订单已存在，幂等跳过 orderNo:{}", order.getOrderNo());
            return false;
        }
    }

    @Override
    public boolean insertCallbackLog(String userId, String orderNo, String outTradeNo,
                                     int notifySeq, String raw, String result) {
        PayCallbackLogPO po = new PayCallbackLogPO();
        po.setUserId(userId);
        po.setOrderNo(orderNo);
        po.setOutTradeNo(outTradeNo);
        po.setNotifySeq(notifySeq);
        // 报文可能很长，截断保存，避免把一张日志表撑爆
        po.setRaw(raw == null ? null : raw.substring(0, Math.min(raw.length(), 2000)));
        po.setResult(result);
        try {
            return payDao.insertCallbackLog(po) == 1;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    public boolean markPaidAndEnqueue(PayOrder order, String outTradeNo,
                                      String topic, String messageId, String payload) {
        Boolean done = transactionTemplate.execute(status -> {
            int updated = payDao.markPaid(order.getOrderNo(), order.getUserId(), outTradeNo);
            if (updated != 1) {
                status.setRollbackOnly();
                return false;
            }
            // 业务成功 ⇒ 消息一定入库；投递失败由 outbox 重试
            outboxService.append(order.getUserId(), topic, messageId, payload);
            return true;
        });
        return Boolean.TRUE.equals(done);
    }

    @Override
    public boolean markGranted(String orderNo, String userId) {
        return payDao.markGranted(orderNo, userId) == 1;
    }

    @Override
    public boolean markClosed(String orderNo, String userId) {
        return payDao.markClosed(orderNo, userId) == 1;
    }

    @Override
    public List<PayOrder> queryTimeoutOrders(java.util.Date deadline, int limit) {
        List<PayOrderPO> rows = payDao.queryTimeoutOrders(deadline, limit);
        List<PayOrder> result = new ArrayList<>(rows.size());
        for (PayOrderPO po : rows) {
            result.add(toOrder(po));
        }
        return result;
    }

    @Override
    public boolean markRefunded(String orderNo, String userId, int refundedCent) {
        return payDao.markRefunded(orderNo, userId, refundedCent) == 1;
    }

    @Override
    public List<PayOrder> queryOrdersByState(String state, int limit) {
        List<PayOrderPO> rows = payDao.queryOrdersByState(state, limit);
        List<PayOrder> result = new ArrayList<>(rows.size());
        for (PayOrderPO po : rows) {
            result.add(toOrder(po));
        }
        return result;
    }

    @Override
    public void saveReconcileDiff(com.sjs.marketing.domain.pay.model.PayReconcileDiff diff) {
        com.sjs.marketing.infrastructure.dao.po.PayReconcileDiffPO po =
                new com.sjs.marketing.infrastructure.dao.po.PayReconcileDiffPO();
        po.setRunId(diff.getRunId());
        po.setOrderNo(diff.getOrderNo());
        po.setUserId(diff.getUserId());
        po.setOrderState(diff.getOrderState());
        po.setOrderAmount(diff.getOrderAmount());
        po.setLedgerAmount(diff.getLedgerAmount());
        po.setRefundedCent(diff.getRefundedCent());
        po.setReason(diff.getReason());
        po.setState(diff.getState());
        payDao.insertReconcileDiff(po);
    }

    @Override
    public List<com.sjs.marketing.domain.pay.model.PayReconcileDiff> queryReconcileDiffs(String userId, int limit) {
        List<com.sjs.marketing.infrastructure.dao.po.PayReconcileDiffPO> rows = payDao.queryReconcileDiffs(userId, limit);
        List<com.sjs.marketing.domain.pay.model.PayReconcileDiff> result = new ArrayList<>(rows.size());
        for (com.sjs.marketing.infrastructure.dao.po.PayReconcileDiffPO po : rows) {
            result.add(com.sjs.marketing.domain.pay.model.PayReconcileDiff.builder()
                    .id(po.getId())
                    .runId(po.getRunId())
                    .orderNo(po.getOrderNo())
                    .userId(po.getUserId())
                    .orderState(po.getOrderState())
                    .orderAmount(po.getOrderAmount())
                    .ledgerAmount(po.getLedgerAmount())
                    .refundedCent(po.getRefundedCent())
                    .reason(po.getReason())
                    .state(po.getState())
                    .createTime(po.getCreateTime())
                    .build());
        }
        return result;
    }

    private PayOrder toOrder(PayOrderPO po) {
        if (po == null) {
            return null;
        }
        return PayOrder.builder()
                .orderNo(po.getOrderNo())
                .userId(po.getUserId())
                .sku(po.getSku())
                .productType(po.getProductType())
                .amount(po.getAmount())
                .amountCent(po.getAmountCent())
                .state(po.getState())
                .outTradeNo(po.getOutTradeNo())
                .paidAt(po.getPaidAt())
                .grantedAt(po.getGrantedAt())
                .closedAt(po.getClosedAt())
                .refundedCent(po.getRefundedCent())
                .refundedAt(po.getRefundedAt())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }
}
