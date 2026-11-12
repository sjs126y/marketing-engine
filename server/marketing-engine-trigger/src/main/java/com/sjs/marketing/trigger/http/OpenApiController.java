package com.sjs.marketing.trigger.http;

import com.sjs.marketing.domain.activity.adapter.repository.IActivityRepository;
import com.sjs.marketing.domain.activity.model.entity.ActivityEntity;
import com.sjs.marketing.domain.activity.model.entity.ActivitySkuEntity;
import com.sjs.marketing.domain.activity.model.entity.SkuRechargeEntity;
import com.sjs.marketing.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.sjs.marketing.trigger.auth.ApiKeyAuthenticationFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 面向上游业务方的开放接口。
 *
 * <p>由 API Key + HMAC 签名保护（见 {@code ApiKeyAuthenticationFilter}）。
 * 上游系统调用充值接口，把「订单号 + sku」换成用户的活动额度；
 * {@code outBusinessNo} 由上游传入并作为幂等键——这一点不能省，
 * 否则重复请求会重复加额度。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@RestController
@RequestMapping("/open/v1")
public class OpenApiController {

    @Resource
    private IRaffleActivityAccountQuotaService quotaService;

    @Resource
    private IActivityRepository activityRepository;

    /**
     * 额度充值：把一次业务购买转成用户的活动次数。
     */
    @PostMapping("/quota/recharge")
    public ResponseEntity<Map<String, Object>> recharge(
            @RequestAttribute(name = ApiKeyAuthenticationFilter.ATTR_CLIENT, required = false) String client,
            @RequestBody RechargeRequest request) {

        if (request.getUserId() == null || request.getUserId().isBlank()
                || request.getSku() == null
                || request.getOutBusinessNo() == null || request.getOutBusinessNo().isBlank()) {
            return ResponseEntity.badRequest().body(fail("400", "userId / sku / outBusinessNo 均为必填"));
        }

        try {
            SkuRechargeEntity entity = new SkuRechargeEntity();
            entity.setUserId(request.getUserId());
            entity.setSku(request.getSku());
            entity.setOutBusinessNo(request.getOutBusinessNo());

            String orderId = quotaService.createOrder(entity);
            log.info("开放接口充值成功 client: {} userId: {} sku: {} orderId: {}",
                    client, request.getUserId(), request.getSku(), orderId);

            Map<String, Object> body = ok();
            body.put("orderId", orderId);
            body.put("outBusinessNo", request.getOutBusinessNo());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("开放接口充值失败 client: {} userId: {} sku: {}",
                    client, request.getUserId(), request.getSku(), e);
            return ResponseEntity.status(500).body(fail("500", e.getMessage()));
        }
    }

    /**
     * 查询活动状态与 SKU 库存，供上游展示。
     */
    @GetMapping("/activity/{activityId}")
    public ResponseEntity<Map<String, Object>> activity(@PathVariable Long activityId) {
        ActivityEntity activity = activityRepository.queryActivityByActivityId(activityId);
        if (activity == null) {
            return ResponseEntity.status(404).body(fail("404", "活动不存在"));
        }

        List<ActivitySkuEntity> skus = activityRepository.queryActivitySkuListByActivityId(activityId);
        Map<String, Object> body = ok();
        body.put("activityId", activity.getActivityId());
        body.put("activityName", activity.getActivityName());
        body.put("state", activity.getState() == null ? null : activity.getState().getCode());
        body.put("beginTime", activity.getBeginDateTime());
        body.put("endTime", activity.getEndDateTime());
        body.put("strategyId", activity.getStrategyId());
        body.put("skus", skus);
        return ResponseEntity.ok(body);
    }

    private Map<String, Object> ok() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "0000");
        body.put("info", "成功");
        return body;
    }

    private Map<String, Object> fail(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("info", message);
        return body;
    }

    @lombok.Data
    public static class RechargeRequest {
        private String userId;
        private Long sku;
        private String outBusinessNo;
    }

}
