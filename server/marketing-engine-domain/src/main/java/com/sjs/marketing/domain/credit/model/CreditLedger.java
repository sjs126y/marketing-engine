package com.sjs.marketing.domain.credit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 权益流水（append-only）。
 *
 * <p>每一次变动都要留一条，且 {@code bizNo} 唯一——它是记账幂等键，
 * 保证「同一笔业务不会记两次账」。流水不参与业务判断，只用于追溯与对账。</p>
 *
 * @author marketing-engine contributors
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditLedger {

    /** 类型：发放 */
    public static final String TYPE_GRANT = "grant";
    /** 类型：消耗 */
    public static final String TYPE_CONSUME = "consume";
    /** 类型：过期回收 */
    public static final String TYPE_EXPIRE = "expire";
    /** 类型：冲正 */
    public static final String TYPE_REVERSE = "reverse";

    /** 自增ID */
    private Long id;
    /** 用户ID（分片键） */
    private String userId;
    /** 业务幂等号 */
    private String bizNo;
    /** 关联批次 */
    private Long batchId;
    /** 来源场景 */
    private String scene;
    /** 类型 */
    private String type;
    /** 变动值 */
    private Integer delta;
    /** 变动后余额 */
    private Integer balanceAfter;
    /** 创建时间 */
    private Date createTime;
}
