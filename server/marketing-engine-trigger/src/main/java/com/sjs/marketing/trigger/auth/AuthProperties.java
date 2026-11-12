package com.sjs.marketing.trigger.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 鉴权配置。
 *
 * <p>三类主体、三套凭证：</p>
 * <ul>
 *   <li>C 端用户：登录换 JWT，携带 userId；</li>
 *   <li>B 端运营：账号 + BCrypt 口令换 JWT，携带角色；</li>
 *   <li>上游系统：API Key + HMAC 签名 + 时间戳。</li>
 * </ul>
 *
 * <p>{@code enabled=false} 时全部放行，仅用于本地联调与演示；
 * 生产必须开启。</p>
 *
 * @author marketing-engine contributors
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** 总开关；关闭时所有校验跳过（仅本地演示） */
    private boolean enabled = false;

    /** 用户与运营 JWT 的签名密钥（HS256）。长度不足 32 字节会用 SHA-256 派生，避免弱密钥直接上线 */
    private String jwtSecret = "marketing-engine-local-dev-secret-change-me";

    /** 用户端 token 有效期（秒），默认 2 小时 */
    private long userTokenTtlSeconds = 7200;

    /** 运营端 token 有效期（秒），默认 8 小时 */
    private long operatorTokenTtlSeconds = 28800;

    /** 时间戳允许的时钟偏移（秒），防重放 */
    private long timestampSkewSeconds = 300;

    /** API Key 单机限流：窗口内最大请求数 */
    private int rateLimitPerMinute = 600;

    /** 运营账号：用户名 → BCrypt 口令散列 */
    private Map<String, Operator> operators = new LinkedHashMap<>();

    /** 上游接入方：apiKey → 配置 */
    private Map<String, ApiClient> apiClients = new LinkedHashMap<>();

    @Data
    public static class Operator {
        /** BCrypt 散列；为空表示该账号未启用 */
        private String passwordHash;
        /** 角色：ADMIN / OPERATOR */
        private String role = "OPERATOR";
    }

    @Data
    public static class ApiClient {
        /** 共享密钥，用于 HMAC-SHA256 签名 */
        private String secret;
        /** 备注 */
        private String name;
    }

}
