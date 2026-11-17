package com.sjs.marketing.domain.credit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 余额视图：账户汇总 + 「即将过期」提示。
 *
 * <p>主站首页要提示「你有 X 积分将于 Y 到期」，这属于展示信息而不是资产本身，
 * 所以单独包一个 VO，不往 {@link CreditAccount} 里塞展示字段。</p>
 *
 * @author marketing-engine contributors
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditBalanceVO {

    /** 用户ID */
    private String userId;
    /** 可用余额 */
    private Integer balance;
    /** 冻结数量 */
    private Integer frozen;
    /** 累计发放 */
    private Integer totalGranted;
    /** 累计消耗 */
    private Integer totalConsumed;
    /** 累计过期回收 */
    private Integer totalExpired;
    /** 即将过期的数量（窗口内） */
    private Integer expiringSoon;
    /** 最近一个到期时间（无可用批次时为空） */
    private Date nearestExpireAt;
    /** 提醒窗口（天），与 expiringSoon 口径一致 */
    private Integer expiringWindowDays;

    /**
     * 组装余额视图。
     *
     * @param account        账户
     * @param expiringSoon   窗口内到期数量
     * @param nearestExpireAt 最近到期时间
     * @param windowDays     窗口天数
     */
    public static CreditBalanceVO of(CreditAccount account, int expiringSoon, Date nearestExpireAt, int windowDays) {
        CreditAccount safe = account == null ? CreditAccount.empty(null) : account;
        return CreditBalanceVO.builder()
                .userId(safe.getUserId())
                .balance(nvl(safe.getBalance()))
                .frozen(nvl(safe.getFrozen()))
                .totalGranted(nvl(safe.getTotalGranted()))
                .totalConsumed(nvl(safe.getTotalConsumed()))
                .totalExpired(nvl(safe.getTotalExpired()))
                .expiringSoon(expiringSoon)
                .nearestExpireAt(nearestExpireAt)
                .expiringWindowDays(windowDays)
                .build();
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
