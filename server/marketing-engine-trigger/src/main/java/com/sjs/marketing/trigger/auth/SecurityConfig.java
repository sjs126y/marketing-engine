package com.sjs.marketing.trigger.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 安全配置：无状态 JWT + API Key，关闭表单登录与 CSRF。
 *
 * <p>路径规则：</p>
 * <ul>
 *   <li>{@code /api/v1/auth/**}、{@code /console/auth/**}：放行（登录入口）</li>
 *   <li>{@code /open/**}：由 API Key 过滤器校验，需要 {@code ROLE_UPSTREAM}</li>
 *   <li>{@code /console/**}：运营端，需要 {@code ROLE_OPERATOR} 或 {@code ROLE_ADMIN}</li>
 *   <li>其余接口：需要登录（C 端用户 JWT）</li>
 * </ul>
 *
 * <p>{@code app.auth.enabled=false} 时整条链放行，便于本地演示。</p>
 *
 * @author marketing-engine contributors
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuthProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SecurityConfig.class);

    private final AuthProperties properties;

    private final JwtService jwtService;

    private final ApiKeyVerifier apiKeyVerifier;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.info("安全配置已加载 app.auth.enabled={}（false 时全部放行，仅本地演示）", properties.isEnabled());
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);

        if (!properties.isEnabled()) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            http.authorizeHttpRequests(auth -> auth
                            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .requestMatchers("/api/v1/auth/**", "/console/auth/**").permitAll()
                            // 健康检查与指标给运维/探活用，不要求登录
                            .requestMatchers("/actuator/**").permitAll()
                            .requestMatchers("/open/**").hasRole("UPSTREAM")
                            .requestMatchers("/console/**").hasAnyRole("OPERATOR", "ADMIN")
                            .anyRequest().authenticated())
                    .addFilterBefore(new JwtAuthenticationFilter(jwtService, properties),
                            UsernamePasswordAuthenticationFilter.class)
                    .addFilterBefore(new ApiKeyAuthenticationFilter(apiKeyVerifier, properties),
                            UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}
