package com.sjs.marketing.domain.pay.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 支付三方对账差异。
 *
 * <p>三个来源：订单（我们记的「该发多少」）、权益流水（实际入账）、退款记录。
 * 任一不等就落表——和「资产对账」一样，只暴露不修正。</p>
 *
 * @author marketing-engine contributors
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayReconcileDiff {

    /** 差异状态：待处理 */
    public static final String STATE_OPEN = "open";

    private Long id;
    /** 对账批次号 */
    private String runId;
    /** 订单号 */
    private String orderNo;
    /** 用户ID */
    private String userId;
    /** 订单状态 */
    private String orderState;
    /** 订单应发数量 */
    private Integer orderAmount;
    /** 权益流水实际净入账（发放 − 冲正） */
    private Integer ledgerAmount;
    /** 已退金额（分） */
    private Integer refundedCent;
    /** 差异原因 */
    private String reason;
    /** 状态 */
    private String state;
    /** 创建时间 */
    private Date createTime;

    /**
     * 判定一笔订单是否对得上（纯函数，可单测）。
     *
     * @param state        订单状态
     * @param orderAmount  订单应发数量
     * @param ledgerAmount 权益流水净入账（发放 − 冲正）
     * @param reversedAmount 已冲正数量
     * @param refundedCent 已退金额（分）
     * @return 差异原因；一致返回 null
     */
    public static String classify(String state, int orderAmount, int ledgerAmount,
                                   int reversedAmount, int refundedCent) {
        boolean granted = PayOrder.STATE_GRANTED.equals(state);
        boolean refunded = PayOrder.STATE_REFUNDED.equals(state);
        boolean unpaid = PayOrder.STATE_CREATED.equals(state) || PayOrder.STATE_PAID.equals(state) || PayOrder.STATE_CLOSED.equals(state);

        if (granted && ledgerAmount == 0) {
            return "订单已发放，但找不到发放流水（发放事务可能被破坏）";
        }
        if (unpaid && ledgerAmount != 0) {
            return "订单未发放却已有入账流水（可能重复发放或状态未推进）";
        }
        if (granted && ledgerAmount != orderAmount) {
            return "发放数量与订单不一致（订单 " + orderAmount + "，流水 " + ledgerAmount + "）";
        }
        if (refunded && refundedCent > 0 && reversedAmount == 0) {
            return "已退款但没有冲正记录（钱退了，权益没扣回）";
        }
        if (refunded && ledgerAmount != orderAmount - reversedAmount) {
            // 部分退款时净入账 = 已消耗的部分，这是正常的；全退时应为 0
            return "退款后净入账与「应发 − 已冲正」不一致（应 "
                    + (orderAmount - reversedAmount) + "，实际 " + ledgerAmount + "）";
        }
        return null;
    }
}
