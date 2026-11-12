package com.sjs.marketing.trigger.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 上游系统的 API Key + HMAC 认证过滤器。
 *
 * <p>只作用于 {@code /open/**}：校验通过后把调用方（apiKey）写入请求属性，
 * 失败直接返回 401，不进入业务逻辑。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    /** 请求属性：调用方标识 */
    public static final String ATTR_CLIENT = "auth.apiClient";

    private final ApiKeyVerifier verifier;

    private final AuthProperties properties;

    public ApiKeyAuthenticationFilter(ApiKeyVerifier verifier, AuthProperties properties) {
        this.verifier = verifier;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled() || !request.getRequestURI().startsWith("/open/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String apiKey = request.getHeader("X-Api-Key");
        String timestamp = request.getHeader("X-Timestamp");
        String signature = request.getHeader("X-Sign");

        // 请求体需要参与签名，读取一次后由 ContentCachingRequestWrapper 复用
        String body = new String(StreamUtils.copyToByteArray(request.getInputStream()), StandardCharsets.UTF_8);
        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request, body);

        String error = verifier.verify(apiKey, timestamp, signature,
                request.getMethod(), request.getRequestURI(), body);
        if (error != null) {
            log.warn("开放接口鉴权失败 uri: {} apiKey: {} 原因: {}", request.getRequestURI(), apiKey, error);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"401\",\"info\":\"" + error + "\"}");
            return;
        }

        request.setAttribute(ATTR_CLIENT, apiKey);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(apiKey, null,
                        List.of(new SimpleGrantedAuthority("ROLE_UPSTREAM"))));
        try {
            chain.doFilter(wrapped, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

}
