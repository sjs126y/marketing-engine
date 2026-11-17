package com.sjs.marketing.domain.credit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 权益批次：每一笔发放就是一个批次，自带有效期。
 *
 * <p>为什么需要批次？因为权益会过期，消耗时必须知道「先扣哪一批」。
 * 消耗策略是 FIFO（按 {@code expiresAt} 升序），保证用户先花掉快过期的部分。</p>
 *
 * <p>{@code eventId} 是发放幂等键——同一笔业务（同一订单、同一次签到）重复投递时，
 * 唯一索引会让第二次插入返回 0 行，从而不重复入账。</p>
 *
 * @author marketing-engine contributors
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditBatch {

    /** 批次状态：可用 */
    public static final String STATE_ACTIVE = "active";
    /** 批次状态：已用完 */
    public static final String STATE_EXHAUSTED = "exhausted";
    /** 批次状态：已过期回收 */
    public static final String STATE_EXPIRED = "expired";
    /** 批次状态：已冲正 */
    public static final String STATE_REVERSED = "reversed";

    /** 自增ID */
    private Long id;
    /** 用户ID（分片键） */
    private String userId;
    /** 幂等键 */
    private String eventId;
    /** 来源场景 */
    private String scene;
    /** 发放数量 */
    private Integer amount;
    /** 剩余可用 */
    private Integer remaining;
    /** 发放时间 */
    private Date grantedAt;
    /** 过期时间 */
    private Date expiresAt;
    /** 状态 */
    private String state;
}
