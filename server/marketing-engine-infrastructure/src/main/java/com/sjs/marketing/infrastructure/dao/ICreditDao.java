package com.sjs.marketing.infrastructure.dao;

import com.sjs.marketing.infrastructure.dao.po.CreditAccountPO;
import com.sjs.marketing.infrastructure.dao.po.CreditBatchPO;
import com.sjs.marketing.infrastructure.dao.po.CreditLedgerPO;
import com.sjs.marketing.infrastructure.dao.po.CreditReconcileDiffPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 权益中心资产表数据访问。
 *
 * <p>三张表都按 {@code user_id} 分库，因此除「扫到期批次」这类后台任务外，
 * 所有 SQL 都带 {@code user_id}，保证精准路由、不被广播到两个库。</p>
 *
 * @author marketing-engine contributors
 */
@Mapper
public interface ICreditDao {

    // ---------- 账户 ----------

    int insertAccount(CreditAccountPO account);

    /** 发放时累加余额与累计发放 */
    int increaseAccountGrant(@Param("userId") String userId, @Param("amount") int amount);

    /** 条件扣减：余额不足时返回 0，调用方回滚 */
    int deductAccount(@Param("userId") String userId, @Param("amount") int amount);

    /** 过期回收时累加「累计过期」并扣减余额 */
    int increaseAccountExpired(@Param("userId") String userId, @Param("amount") int amount);

    CreditAccountPO queryAccount(@Param("userId") String userId);

    // ---------- 批次 ----------

    int insertBatch(CreditBatchPO batch);

    /** 未过期、仍有剩余的批次，按过期时间升序（FIFO） */
    List<CreditBatchPO> queryActiveBatches(@Param("userId") String userId, @Param("now") Date now);

    /** 条件扣减批次剩余：剩余不足返回 0 */
    int deductBatchRemaining(@Param("id") Long id, @Param("userId") String userId, @Param("amount") int amount);

    int markBatchExhausted(@Param("id") Long id, @Param("userId") String userId);

    int markBatchExpired(@Param("id") Long id, @Param("userId") String userId);

    /** 后台任务：扫描已到期且仍可用的批次（不带分片键，广播到各分片） */
    List<CreditBatchPO> queryExpireDue(@Param("now") Date now, @Param("limit") int limit);

    // ---------- 流水 ----------

    int insertLedger(CreditLedgerPO ledger);

    List<CreditLedgerPO> queryLedgers(@Param("userId") String userId, @Param("limit") int limit);

    /** 幂等判定：该业务号是否已经记过账（消耗重放时直接返回上次结果） */
    int countLedgerByBizNo(@Param("userId") String userId, @Param("bizNo") String bizNo);

    // ---------- 对账 / 过期统计 ----------

    /** 后台任务：取一批账户（不带分片键，广播各分片） */
    List<CreditAccountPO> queryAllAccounts(@Param("limit") int limit);

    /**
     * 未核销批次剩余之和。
     *
     * <p>只按 {@code state='active'} 过滤，不看是否已到期：到期但尚未被回收的批次，
     * 其剩余仍在账户余额里，排除掉会让对账报出假差异。</p>
     */
    Integer sumActiveRemaining(@Param("userId") String userId);

    /** 流水累计（发放 − 消耗 − 过期） */
    Integer sumLedgerDelta(@Param("userId") String userId);

    /** 指定窗口内到期的剩余量 */
    Integer sumExpiringRemaining(@Param("userId") String userId, @Param("from") Date from, @Param("to") Date to);

    /** 最近一个到期时间 */
    Date queryNearestExpire(@Param("userId") String userId);

    // ---------- 后台任务（分桶扫描 / 全量对账） ----------

    /** 扫描 {@code [from, to)} 内到期的批次，走 idx_state_expire（不带分片键，广播各分片） */
    List<CreditBatchPO> queryExpireDueBetween(@Param("from") Date from, @Param("to") Date to, @Param("limit") int limit);

    /** 按 user_id 游标分页取账户：id 是各分片自增的，跨库 order by id 会漏账 */
    List<CreditAccountPO> queryAccountsAfter(@Param("afterUserId") String afterUserId, @Param("limit") int limit);

    int insertReconcileDiff(CreditReconcileDiffPO diff);

    List<CreditReconcileDiffPO> queryReconcileDiffs(@Param("runId") String runId,
                                                   @Param("userId") String userId,
                                                   @Param("limit") int limit);
}
