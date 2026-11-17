package com.sjs.marketing.domain.credit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 对账差异：一次对账中「三个来源对不上」的那条记录。
 *
 * <p>设计立场是<b>只暴露、不修正</b>：自动修数会把「记账 bug」变成「数据悄悄被改」，
 * 事后没有任何痕迹。所以差异落表 + 告警，修不修由人决定。</p>
 *
 * @author marketing-engine contributors
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditReconcileDiff {

    /** 差异状态：待处理 */
    public static final String STATE_OPEN = "open";
    /** 差异状态：已确认（人工忽略） */
    public static final String STATE_IGNORED = "ignored";

    /** 自增ID */
    private Long id;
    /** 对账批次号（一次全量对账一个 runId） */
    private String runId;
    /** 用户ID */
    private String userId;
    /** 账户余额 */
    private Integer accountBalance;
    /** 未核销批次剩余之和 */
    private Integer batchRemaining;
    /** 流水累计 */
    private Integer ledgerSum;
    /** 账户 − 批次 */
    private Integer diff;
    /** 差异原因（人可读，落表后运营直接看这条） */
    private String reason;
    /** 状态：open / ignored */
    private String state;
    /** 创建时间 */
    private Date createTime;

    /**
     * 差异归类：把「哪两个值不符」翻译成人能看懂的一句话。
     *
     * <p>抽成纯函数是为了能单测——对账任务本身依赖数据库，不适合在单测里跑。</p>
     *
     * @param account 账户余额
     * @param batch   未核销批次剩余之和
     * @param ledger  流水累计
     * @return 差异原因；三者一致时返回「一致」
     */
    public static String classify(int account, int batch, int ledger) {
        List<String> reasons = new ArrayList<>();
        if (account > batch) {
            reasons.add("账户余额 > 批次剩余（余额多记，或批次被多扣）");
        } else if (account < batch) {
            reasons.add("账户余额 < 批次剩余（余额少记，或批次未扣干净）");
        }
        if (ledger > account) {
            reasons.add("流水累计 > 账户余额（记了账但账户没动，或账户被少加）");
        } else if (ledger < account) {
            reasons.add("流水累计 < 账户余额（账户动了但没记账）");
        }
        return reasons.isEmpty() ? "一致" : String.join("；", reasons);
    }

    /**
     * 由对账结果生成差异记录。
     *
     * @param runId 对账批次号
     * @param vo    对账结果
     */
    public static CreditReconcileDiff from(String runId, CreditReconcileVO vo) {
        int account = vo.getAccountBalance() == null ? 0 : vo.getAccountBalance();
        int batch = vo.getBatchRemaining() == null ? 0 : vo.getBatchRemaining();
        int ledger = vo.getLedgerSum() == null ? 0 : vo.getLedgerSum();
        return CreditReconcileDiff.builder()
                .runId(runId)
                .userId(vo.getUserId())
                .accountBalance(account)
                .batchRemaining(batch)
                .ledgerSum(ledger)
                .diff(account - batch)
                .reason(classify(account, batch, ledger))
                .state(STATE_OPEN)
                .createTime(new Date())
                .build();
    }
}
