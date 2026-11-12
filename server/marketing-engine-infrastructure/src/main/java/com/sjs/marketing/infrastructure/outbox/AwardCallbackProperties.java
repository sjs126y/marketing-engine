package com.sjs.marketing.infrastructure.outbox;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 发奖回调的配置。
 *
 * <p>中台把「用户中了什么」主动推给上游业务系统（HTTP POST）。上游收到后自己入账，
 * 这条链路和中台投 MQ 一样走 outbox，因此天然带重试、退避与死信。</p>
 *
 * @author marketing-engine contributors
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.callback")
public class AwardCallbackProperties {

    /** 是否启用回调；关闭时只做内部 MQ 投递 */
    private boolean enabled = false;

    /** 上游接收回调的地址 */
    private String url = "http://127.0.0.1:9000/webhook/award";

    /** 回调用 HMAC 签名密钥，必须与上游一致 */
    private String secret = "upstream-callback-secret";

    /** 单次请求超时（毫秒） */
    private int timeoutMs = 5000;

}
