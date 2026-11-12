package com.sjs.marketing.trigger.http;

import com.sjs.marketing.domain.strategy.model.valobj.ConfigViolation;
import com.sjs.marketing.domain.strategy.service.armory.IStrategyArmory;
import com.sjs.marketing.domain.strategy.service.validate.StrategyConfigValidator;
import com.sjs.marketing.domain.strategy.adapter.repository.IStrategyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营端配置发布接口。
 *
 * <p>发布是「校验 → 失效缓存 → 重新预热」的三段式：
 * 校验不过直接拒绝，绝不允许带着错误配置上线。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@RestController
@RequestMapping("/console/v1/strategy")
public class StrategyConsoleController {

    @Resource
    private StrategyConfigValidator validator;

    @Resource
    private IStrategyArmory strategyArmory;

    @Resource
    private IStrategyRepository repository;

    /**
     * 只校验，不改动任何状态。
     */
    @PostMapping("/{strategyId}/validate")
    public ResponseEntity<Map<String, Object>> validate(@PathVariable Long strategyId) {
        List<ConfigViolation> violations = validator.validate(strategyId);
        return ResponseEntity.ok(result(strategyId, violations, false));
    }

    /**
     * 校验通过后发布：失效缓存 + 重新装配概率表。
     */
    @PostMapping("/{strategyId}/publish")
    public ResponseEntity<Map<String, Object>> publish(@PathVariable Long strategyId) {
        List<ConfigViolation> violations = validator.validate(strategyId);
        boolean hasError = violations.stream().anyMatch(ConfigViolation::isError);
        if (hasError) {
            log.warn("策略配置校验未通过，拒绝发布 strategyId: {} 问题数: {}", strategyId, violations.size());
            Map<String, Object> body = result(strategyId, violations, false);
            body.put("code", "4001");
            body.put("info", "配置校验未通过，已拒绝发布");
            return ResponseEntity.badRequest().body(body);
        }

        repository.evictStrategyConfigCache(strategyId);
        strategyArmory.assembleLotteryStrategy(strategyId);
        log.info("策略配置已发布 strategyId: {} 警告数: {}", strategyId,
                violations.stream().filter(v -> !v.isError()).count());

        return ResponseEntity.ok(result(strategyId, violations, true));
    }

    private Map<String, Object> result(Long strategyId, List<ConfigViolation> violations, boolean published) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "0000");
        body.put("info", published ? "已发布" : "校验完成");
        body.put("strategyId", strategyId);
        body.put("published", published);
        body.put("errorCount", violations.stream().filter(ConfigViolation::isError).count());
        body.put("warnCount", violations.stream().filter(v -> !v.isError()).count());
        body.put("violations", violations);
        return body;
    }

}
