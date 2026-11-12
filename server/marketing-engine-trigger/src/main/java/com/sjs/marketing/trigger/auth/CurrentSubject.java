package com.sjs.marketing.trigger.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 从请求中取当前认证主体。
 *
 * <p>鉴权开启时，userId 一律以 token 为准，忽略请求体里自报的值——
 * 否则任何人都能冒充别人抽奖。鉴权关闭（本地演示）时才回退到入参。</p>
 *
 * @author marketing-engine contributors
 */
@Component
public class CurrentSubject {

    private final AuthProperties properties;

    public CurrentSubject(AuthProperties properties) {
        this.properties = properties;
    }

    /**
     * 解析用户 ID。
     *
     * @param fromRequest 请求体里携带的 userId（仅鉴权关闭时使用）
     * @return 最终生效的 userId
     */
    public String resolveUserId(String fromRequest) {
        if (!properties.isEnabled()) {
            return fromRequest;
        }
        String subject = attribute(JwtAuthenticationFilter.ATTR_SUBJECT);
        String type = attribute(JwtAuthenticationFilter.ATTR_TYPE);
        if (subject != null && JwtService.TYPE_USER.equals(type)) {
            return subject;
        }
        // 开启了鉴权却没有合法的用户 token：交给 Spring Security 拒绝
        return null;
    }

    /**
     * 运营端当前账号。
     */
    public String operator() {
        String subject = attribute(JwtAuthenticationFilter.ATTR_SUBJECT);
        String type = attribute(JwtAuthenticationFilter.ATTR_TYPE);
        return JwtService.TYPE_OPERATOR.equals(type) ? subject : null;
    }

    private String attribute(String name) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        Object value = request.getAttribute(name);
        return value == null ? null : value.toString();
    }

}
