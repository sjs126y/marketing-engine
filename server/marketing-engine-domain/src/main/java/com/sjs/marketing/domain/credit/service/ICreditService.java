package com.sjs.marketing.domain.credit.service;

import com.sjs.marketing.domain.credit.model.CreditAccount;
import com.sjs.marketing.domain.credit.model.CreditLedger;

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
}
