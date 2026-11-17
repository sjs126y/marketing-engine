package com.sjs.marketing.domain.credit.service;

import com.sjs.marketing.domain.credit.model.CreditAccount;
import com.sjs.marketing.domain.credit.model.CreditBalanceVO;
import com.sjs.marketing.domain.credit.model.CreditLedger;
import com.sjs.marketing.domain.credit.model.CreditReconcileDiff;
import com.sjs.marketing.domain.credit.model.CreditReconcileSummary;
import com.sjs.marketing.domain.credit.model.CreditReconcileVO;

import java.util.Date;
import java.util.List;

/**
 * 权益中心资产服务：发放、消耗、查询、过期回收。
 *
 * <p>语义边界：本服务只负责「用户有什么」（资产账），不负责「怎么发」的业务规则
 * （那部分在活动/策略域）。发放入口由业务方带幂等键调用。</p>
 *
 * @author marketing-engine contributors
 */
public interface ICreditService {

    /**
     * 发放权益。
     *
     * @param userId   用户ID
     * @param scene    来源场景（sign/task/lottery/invite/pay/migrate）
     * @param amount   数量（必须 > 0）
     * @param eventId  幂等键：同一笔业务重复调用只入账一次
     * @param ttlDays  有效期天数，null 用默认 30 天
     * @return true = 本次入账；false = 幂等命中（已入过账）
     */
    boolean grant(String userId, String scene, int amount, String eventId, Integer ttlDays);

    /**
     * 消耗权益（FIFO：先扣快过期的批次）。
     *
     * @param bizNo 业务幂等号：同一笔业务重复调用只扣一次
     * @return 消耗后的余额
     */
    int consume(String userId, String scene, int amount, String bizNo);

    /** 查询账户（含余额） */
    CreditAccount queryAccount(String userId);

    /** 查询流水 */
    List<CreditLedger> queryLedgers(String userId, int limit);

    /**
     * 过期回收：扫描到期批次并按批次回收。
     *
     * @return 本次回收的批次数
     */
    int expireDue(int limit);

    /**
     * 只在 {@code [from, to)} 窗口内做一次过期回收（任务按自然日分桶调用）。
     *
     * <p>返回本次回收的批次数；回收动作靠「条件更新抢到才动钱」保证多实例幂等。</p>
     */
    int expireDueBetween(Date from, Date to, int limit);

    /**
     * 按自然日分桶做一轮过期回收（任务与运营接口共用同一条路径）。
     *
     * <p>先扫「今天 − lookbackDays 天 ~ 今天」，再对更早的遗留做一次全量兜底，
     * 所以停机多久都不会有「永久漏回收」。</p>
     *
     * @param lookbackDays 回看天数
     * @param batchSize    单次查询条数
     * @param maxBatches   本轮最多回收的批次数
     * @return 本次回收的批次数
     */
    int expireByWindow(int lookbackDays, int batchSize, int maxBatches);

    /** 余额视图：账户汇总 + 即将过期提示（主站首页要展示） */
    CreditBalanceVO queryBalance(String userId);

    /**
     * 单用户对账：账户余额 / 未核销批次剩余之和 / 流水累计，三值比对。
     *
     * <p>只返回结果，不修数据——发现错误的手段和修正错误的手段必须分开。</p>
     */
    CreditReconcileVO reconcile(String userId);

    /**
     * 全量对账：按 {@code user_id} 游标分页扫账户，不一致的落差异表并告警。
     *
     * @param pageSize 每页账户数（<=0 或 >1000 时取默认 200）
     */
    CreditReconcileSummary reconcileAll(int pageSize);

    /** 查询对账差异（最近 N 条，userId 为空表示不按用户过滤） */
    List<CreditReconcileDiff> queryReconcileDiffs(String userId, int limit);
}
