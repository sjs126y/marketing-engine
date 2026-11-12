package com.sjs.marketing.trigger.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 上游系统的 API Key 校验。
 *
 * <p>签名规则：{@code HMAC-SHA256(secret, method + "\n" + path + "\n" + timestamp + "\n" + body)}，
 * 请求头带 {@code X-Api-Key / X-Timestamp / X-Sign}。时间戳只接受偏移窗口内的值，防重放。</p>
 *
 * <p>限流目前是单机滑动窗口，够用且无外部依赖；多实例部署时应换成 Redis 令牌桶，
 * 这一点在 README 的已知限制里有说明。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Component
public class ApiKeyVerifier {

    private final AuthProperties properties;

    /** apiKey → 当前分钟的请求计数 */
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public ApiKeyVerifier(AuthProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验请求签名。
     *
     * @return 校验失败的原因；通过时返回 {@code null}
     */
    public String verify(String apiKey, String timestamp, String signature, String method, String path, String body) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "缺少 X-Api-Key";
        }
        AuthProperties.ApiClient client = properties.getApiClients().get(apiKey);
        if (client == null || client.getSecret() == null) {
            return "未知的 API Key";
        }
        if (timestamp == null || signature == null) {
            return "缺少 X-Timestamp 或 X-Sign";
        }

        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return "时间戳格式非法";
        }
        long now = System.currentTimeMillis() / 1000;
        if (Math.abs(now - ts) > properties.getTimestampSkewSeconds()) {
            return "请求已过期或时间戳超前";
        }

        String expected = sign(client.getSecret(), method, path, timestamp, body);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            return "签名不匹配";
        }

        if (!acquire(apiKey)) {
            return "请求过于频繁";
        }
        return null;
    }

    /**
     * 计算签名。
     */
    public String sign(String secret, String method, String path, String timestamp, String body) {
        String payload = method + "\n" + path + "\n" + timestamp + "\n" + (body == null ? "" : body);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("计算签名失败", e);
        }
    }

    private boolean acquire(String apiKey) {
        long minute = System.currentTimeMillis() / 60000;
        Window window = windows.computeIfAbsent(apiKey, k -> new Window());
        synchronized (window) {
            if (window.minute != minute) {
                window.minute = minute;
                window.count = 0;
            }
            if (window.count >= properties.getRateLimitPerMinute()) {
                return false;
            }
            window.count++;
            return true;
        }
    }

    private static final class Window {
        private long minute = -1;
        private int count = 0;
    }

}
