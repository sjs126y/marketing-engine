package com.sjs.marketing.infrastructure.outbox;

/**
 * outbox 消息状态。
 *
 * @author marketing-engine contributors
 */
public enum OutboxState {

    /** 待投递 */
    PENDING("pending"),
    /** 已被某个实例抢占，正在投递 */
    SENDING("sending"),
    /** 已投递到 MQ，等待消费回执 */
    SENT("sent"),
    /** 消费端已处理完成 */
    CONSUMED("consumed"),
    /** 重试耗尽，需人工处理 */
    DEAD("dead"),
    ;

    private final String code;

    OutboxState(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

}
