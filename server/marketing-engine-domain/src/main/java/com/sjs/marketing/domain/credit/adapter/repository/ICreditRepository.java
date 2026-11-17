package com.sjs.marketing.domain.credit.adapter.repository;

import com.sjs.marketing.domain.credit.model.CreditAccount;
import com.sjs.marketing.domain.credit.model.CreditBatch;
import com.sjs.marketing.domain.credit.model.CreditLedger;
import com.sjs.marketing.domain.credit.model.CreditReconcileDiff;

import java.util.Date;
import java.util.List;

/**
 * 权益资产仓储。
 *
 * <p>约定：写操作都在实现里用本地事务把「账户 + 批次 + 流水」包在一起。
 * 三张表同按 {@code user_id} 分库，因此同一用户的写落在同一库，一个本地事务即可覆盖。</p>
 *
 * @author marketing-engine contributors
 */
public interface ICreditRepository {

    /**
     * 发放：插入批次 + 累加账户 + 记流水。
     *
     * @return true = 本次入账成功；false = eventId 已存在（幂等命中，视为成功但未重复加钱）
     */
    boolean grant(String userId, String scene, int amount, String eventId, Date grantedAt, Date expiresAt);

    /**
     * 消耗：按 {@code expiresAt} 升序逐批扣减（先花快过期的），扣账户并记流水。
     *
     * @return 消耗后的余额
     */
    int consume(String userId, String scene, int amount, String bizNo);

    /** 查询账户（不存在返回空账户） */
    CreditAccount queryAccount(String userId);

    /** 查询流水（按时间倒序） */
    List<CreditLedger> queryLedgers(String userId, int limit);

    /** 扫描到期未回收的批次 */
    List<CreditBatch> queryExpireDue(Date now, int limit);

    /** 过期回收一个批次：批次置 expired + 账户扣减 + 记 expire 流水 */
    boolean expire(CreditBatch batch);

    // ---------- 过期分桶扫描 ----------

    /**
     * 扫描 {@code [from, to)} 内到期的可用批次。
     *
     * <p>走 {@code idx_state_expire(state, expires_at)}，任务按自然日分桶调用，
     * 每次只回表一天的量；全量兜底仍用 {@link #queryExpireDue(Date, int)}。</p>
     */
    List<CreditBatch> queryExpireDueBetween(Date from, Date to, int limit);

    // ---------- 对账 / 提醒统计 ----------

    /**
     * 未核销批次剩余之和。
     *
     * <p>只按 {@code state='active'} 过滤，<b>不看是否已到期</b>：到期但还没被任务回收的批次，
     * 其剩余仍在账户余额里，若这里把它排除掉，对账会报出并不存在的差异。</p>
     */
    int sumActiveBatchRemaining(String userId);

    /** 流水累计（发放 − 消耗 − 过期），与账户余额口径一致 */
    int sumLedgerDelta(String userId);

    /** 窗口内到期的剩余量（即将过期提醒） */
    int sumExpiringRemaining(String userId, Date from, Date to);

    /** 最近一个到期时间（没有可用批次时返回 null） */
    Date queryNearestExpire(String userId);

    /**
     * 按 {@code user_id} 游标分页取账户，供全量对账任务使用。
     *
     * <p>不能用 {@code order by id limit}：id 是各分片各自自增的，跨库排序再截断会漏账、重账。</p>
     */
    List<CreditAccount> queryAccountsAfter(String afterUserId, int limit);

    /** 落一条对账差异 */
    void saveReconcileDiff(CreditReconcileDiff diff);

    /** 查对账差异；{@code runId} / {@code userId} 为空表示不加该条件 */
    List<CreditReconcileDiff> queryReconcileDiffs(String runId, String userId, int limit);
}
