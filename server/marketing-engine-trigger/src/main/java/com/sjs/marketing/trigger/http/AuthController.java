package com.sjs.marketing.trigger.http;

import com.sjs.marketing.trigger.auth.AuthProperties;
import com.sjs.marketing.trigger.auth.JwtService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 登录入口。
 *
 * <p>C 端用户只做身份换取（demo 无真实用户体系，userId 自报）；
 * 运营端校验 BCrypt 口令并签发带角色的 token。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@RestController
public class AuthController {

    private final JwtService jwtService;

    private final AuthProperties properties;

    private final PasswordEncoder passwordEncoder;

    public AuthController(JwtService jwtService, AuthProperties properties, PasswordEncoder passwordEncoder) {
        this.jwtService = jwtService;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<Map<String, Object>> userLogin(@RequestBody UserLoginRequest request) {
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            return ResponseEntity.badRequest().body(fail("userId 不能为空"));
        }
        String token = jwtService.issueUserToken(request.getUserId());
        Map<String, Object> body = ok();
        body.put("token", token);
        body.put("userId", request.getUserId());
        body.put("expiresIn", properties.getUserTokenTtlSeconds());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/console/auth/login")
    public ResponseEntity<Map<String, Object>> operatorLogin(@RequestBody OperatorLoginRequest request) {
        AuthProperties.Operator operator = properties.getOperators().get(request.getUsername());
        if (operator == null || operator.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), operator.getPasswordHash())) {
            log.warn("运营端登录失败 username: {}", request.getUsername());
            return ResponseEntity.status(401).body(fail("账号或口令错误"));
        }

        String token = jwtService.issueOperatorToken(request.getUsername(), operator.getRole());
        Map<String, Object> body = ok();
        body.put("token", token);
        body.put("username", request.getUsername());
        body.put("role", operator.getRole());
        body.put("expiresIn", properties.getOperatorTokenTtlSeconds());
        return ResponseEntity.ok(body);
    }

    private Map<String, Object> ok() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "0000");
        body.put("info", "成功");
        return body;
    }

    private Map<String, Object> fail(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "401");
        body.put("info", message);
        return body;
    }

    @Data
    public static class UserLoginRequest {
        private String userId;
    }

    @Data
    public static class OperatorLoginRequest {
        private String username;
        private String password;
    }

}
