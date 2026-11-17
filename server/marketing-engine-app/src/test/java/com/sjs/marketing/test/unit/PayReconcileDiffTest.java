package com.sjs.marketing.test.unit;

import com.sjs.marketing.domain.pay.model.PayOrder;
import com.sjs.marketing.domain.pay.model.PayReconcileDiff;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 支付三方对账规则验证。
 *
 * <p>对账的价值不在「跑出 0 差异」，而在差异能被归类到具体环节：
 * 发放没发生、金额不对、还是退款没冲正。部分退款是合法状态，
 * 净入账应当等于「已消耗部分」，这里专门有一条用例守住它。</p>
 *
 * @author marketing-engine contributors
 */
class PayReconcileDiffTest {

    @Test
    @DisplayName("发放成功且流水一致：无差异")
    void shouldPassWhenGrantedAndMatched() {
        assertThat(PayReconcileDiff.classify(PayOrder.STATE_GRANTED, 100, 100, 0, 0)).isNull();
    }

    @Test
    @DisplayName("订单已发放但没有流水：发放事务被破坏")
    void shouldDetectMissingGrantLedger() {
        assertThat(PayReconcileDiff.classify(PayOrder.STATE_GRANTED, 100, 0, 0, 0))
                .contains("找不到发放流水");
    }

    @Test
    @DisplayName("未发放却已有入账：重复发放或状态没推进")
    void shouldDetectUnexpectedLedger() {
        assertThat(PayReconcileDiff.classify(PayOrder.STATE_PAID, 100, 100, 0, 0))
                .contains("未发放却已有入账流水");
        assertThat(PayReconcileDiff.classify(PayOrder.STATE_CLOSED, 100, 100, 0, 0))
                .contains("未发放却已有入账流水");
    }

    @Test
    @DisplayName("发放数量与订单不一致")
    void shouldDetectAmountMismatch() {
        assertThat(PayReconcileDiff.classify(PayOrder.STATE_GRANTED, 100, 80, 0, 0))
                .contains("发放数量与订单不一致");
    }

    @Test
    @DisplayName("全额退款且已冲正：净入账 0，无差异")
    void shouldPassWhenFullyRefunded() {
        assertThat(PayReconcileDiff.classify(PayOrder.STATE_REFUNDED, 100, 0, 100, 100)).isNull();
    }

    @Test
    @DisplayName("部分退款：净入账 = 已消耗部分，不算差异")
    void shouldPassWhenPartiallyRefunded() {
        // 发了 100，消耗 30，冲正 70，退款 70 分 → 净入账 30 = 已消耗
        assertThat(PayReconcileDiff.classify(PayOrder.STATE_REFUNDED, 100, 30, 70, 70)).isNull();
    }

    @Test
    @DisplayName("已退款但没有冲正记录：钱退了权益没扣回")
    void shouldDetectRefundWithoutReversal() {
        assertThat(PayReconcileDiff.classify(PayOrder.STATE_REFUNDED, 100, 100, 0, 100))
                .contains("没有冲正记录");
    }

    @Test
    @DisplayName("退款后净入账与应发−已冲正不一致")
    void shouldDetectRefundNetMismatch() {
        assertThat(PayReconcileDiff.classify(PayOrder.STATE_REFUNDED, 100, 90, 60, 60))
                .contains("不一致");
    }
}
