package com.sjs.marketing.trigger.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Bearer token 认证过滤器。
 *
 * <p>校验通过后把主体写入 SecurityContext，并放到请求属性
 * {@link #ATTR_USER_ID} / {@link #ATTR_ROLE}，控制器用
 * {@code @RequestAttribute} 取值，不再相信请求体里的 userId。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 请求属性：认证主体（用户 ID 或运营账号） */
    public static final String ATTR_SUBJECT = "auth.subject";
    /** 请求属性：主体类型（user / operator） */
    public static final String ATTR_TYPE = "auth.type";
    /** 请求属性：角色（运营端才有） */
    public static final String ATTR_ROLE = "auth.role";

    private final JwtService jwtService;

    private final AuthProperties properties;

    public JwtAuthenticationFilter(JwtService jwtService, AuthProperties properties) {
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!properties.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            Optional<Claims> claims = jwtService.parse(header.substring(7).trim());
            if (claims.isPresent()) {
                Claims body = claims.get();
                String subject = body.getSubject();
                String type = body.get(JwtService.CLAIM_TYPE, String.class);
                String role = body.get(JwtService.CLAIM_ROLE, String.class);

                request.setAttribute(ATTR_SUBJECT, subject);
                request.setAttribute(ATTR_TYPE, type);
                request.setAttribute(ATTR_ROLE, role);

                List<SimpleGrantedAuthority> authorities = role == null
                        ? List.of()
                        : List.of(new SimpleGrantedAuthority("ROLE_" + role));
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(subject, null, authorities));
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

}
