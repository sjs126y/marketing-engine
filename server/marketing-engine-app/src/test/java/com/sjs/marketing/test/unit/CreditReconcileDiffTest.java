package com.sjs.marketing.test.unit;

import com.sjs.marketing.domain.credit.model.CreditReconcileDiff;
import com.sjs.marketing.domain.credit.model.CreditReconcileVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对账差异归类的验证。
 *
 * <p>对账的价值不在「跑出 0 差异」，而在「差异能被说清楚是哪一类」——
 * 所以 {@code classify} 是纯函数、单独测，不依赖数据库。</p>
 *
 * @author marketing-engine contributors
 */
class CreditReconcileDiffTest {

    @Test
    @DisplayName("三值一致：没有差异")
    void shouldBeConsistentWhenAllThreeMatch() {
        CreditReconcileVO vo = CreditReconcileVO.of("alice", 100, 100, 100);

        assertThat(vo.getConsistent()).isTrue();
        assertThat(vo.getDiff()).isZero();
        assertThat(CreditReconcileDiff.classify(100, 100, 100)).isEqualTo("一致");
    }

    @Test
    @DisplayName("账户 > 批次：余额多记或批次被多扣")
    void shouldDetectAccountGreaterThanBatch() {
        CreditReconcileVO vo = CreditReconcileVO.of("alice", 101, 100, 101);

        assertThat(vo.getConsistent()).isFalse();
        assertThat(vo.getDiff()).isEqualTo(1);
        assertThat(CreditReconcileDiff.classify(101, 100, 101))
                .contains("账户余额 > 批次剩余");
    }

    @Test
    @DisplayName("账户 < 批次：余额少记或批次没扣干净")
    void shouldDetectAccountLessThanBatch() {
        assertThat(CreditReconcileDiff.classify(98, 100, 98))
                .contains("账户余额 < 批次剩余");
    }

    @Test
    @DisplayName("流水与账户不符：记了账但账户没动/动了没记账")
    void shouldDetectLedgerMismatch() {
        assertThat(CreditReconcileDiff.classify(100, 100, 95))
                .contains("流水累计 < 账户余额");
        assertThat(CreditReconcileDiff.classify(100, 100, 105))
                .contains("流水累计 > 账户余额");
    }

    @Test
    @DisplayName("差异记录由对账结果转换：字段对齐、状态为 open")
    void shouldBuildDiffFromVO() {
        CreditReconcileVO vo = CreditReconcileVO.of("bob", 50, 45, 50);
        CreditReconcileDiff diff = CreditReconcileDiff.from("R20261117", vo);

        assertThat(diff.getRunId()).isEqualTo("R20261117");
        assertThat(diff.getUserId()).isEqualTo("bob");
        assertThat(diff.getAccountBalance()).isEqualTo(50);
        assertThat(diff.getBatchRemaining()).isEqualTo(45);
        assertThat(diff.getLedgerSum()).isEqualTo(50);
        assertThat(diff.getDiff()).isEqualTo(5);
        assertThat(diff.getState()).isEqualTo(CreditReconcileDiff.STATE_OPEN);
        assertThat(diff.getReason()).isNotBlank();
    }
}
