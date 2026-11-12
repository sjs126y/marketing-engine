package com.sjs.marketing.trigger.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * JWT 签发与校验（HS256）。
 *
 * <p>密钥不足 32 字节时用 SHA-256 派生，避免弱密钥直接上线导致签名可伪造。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Component
public class JwtService {

    /** 载荷：主体类型 */
    public static final String CLAIM_TYPE = "typ";
    /** 载荷：角色 */
    public static final String CLAIM_ROLE = "role";

    /** 主体类型：C 端用户 */
    public static final String TYPE_USER = "user";
    /** 主体类型：B 端运营 */
    public static final String TYPE_OPERATOR = "operator";

    private final AuthProperties properties;

    private final SecretKey key;

    public JwtService(AuthProperties properties) {
        this.properties = properties;
        this.key = buildKey(properties.getJwtSecret());
    }

    /**
     * 签发用户 token。
     */
    public String issueUserToken(String userId) {
        return issue(userId, TYPE_USER, null, properties.getUserTokenTtlSeconds());
    }

    /**
     * 签发运营 token。
     */
    public String issueOperatorToken(String username, String role) {
        return issue(username, TYPE_OPERATOR, role, properties.getOperatorTokenTtlSeconds());
    }

    /**
     * 解析并校验 token，失败返回空。
     */
    public Optional<Claims> parse(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 校验失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String issue(String subject, String type, String role, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(subject)
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_ROLE, role)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private SecretKey buildKey(String secret) {
        byte[] raw = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            try {
                raw = MessageDigest.getInstance("SHA-256").digest(raw);
            } catch (Exception e) {
                throw new IllegalStateException("无法派生 JWT 密钥", e);
            }
        }
        return Keys.hmacShaKeyFor(raw);
    }

}
