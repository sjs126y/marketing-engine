package com.sjs.marketing.domain.credit.adapter.repository;

import com.sjs.marketing.domain.credit.model.CreditAccount;
import com.sjs.marketing.domain.credit.model.CreditBatch;
import com.sjs.marketing.domain.credit.model.CreditLedger;

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
}
