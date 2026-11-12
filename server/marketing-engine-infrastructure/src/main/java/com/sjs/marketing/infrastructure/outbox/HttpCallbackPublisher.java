package com.sjs.marketing.infrastructure.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

/**
 * HTTP 回调投递器。
 *
 * <p>把发奖结果 POST 给上游，带两个头：</p>
 * <ul>
 *   <li>{@code X-Event-Id}：幂等键，上游据此去重；</li>
 *   <li>{@code X-Sign}：{@code HMAC-SHA256(secret, body)}，上游据此验真身。</li>
 * </ul>
 *
 * <p>非 2xx 视为失败，由 outbox 的重试机制接管；因为上游拿到了 X-Event-Id，
 * 重复投递不会重复入账。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Component
public class HttpCallbackPublisher {

    private final AwardCallbackProperties properties;

    private final HttpClient httpClient;

    public HttpCallbackPublisher(AwardCallbackProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build();
    }

    /**
     * 投递一次回调。
     *
     * @param url     目标地址
     * @param eventId 幂等键
     * @param payload JSON 消息体
     * @throws IOException 网络错误或非 2xx，交由上层重试
     */
    public void publish(String url, String eventId, String payload) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("X-Event-Id", eventId)
                .header("X-Sign", sign(payload))
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("回调返回 HTTP " + response.statusCode()
                    + " body=" + truncate(response.body()));
        }
        log.info("发奖回调成功 url:{} eventId:{} status:{}", url, eventId, response.statusCode());
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("计算回调签名失败", e);
        }
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 200 ? text : text.substring(0, 200);
    }

}
