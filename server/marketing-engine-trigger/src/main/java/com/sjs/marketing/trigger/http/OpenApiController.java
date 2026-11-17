package com.sjs.marketing.trigger.http;

import com.sjs.marketing.domain.activity.adapter.repository.IActivityRepository;
import com.sjs.marketing.domain.activity.model.entity.ActivityEntity;
import com.sjs.marketing.domain.activity.model.entity.ActivitySkuEntity;
import com.sjs.marketing.domain.activity.model.entity.SkuRechargeEntity;
import com.sjs.marketing.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.sjs.marketing.domain.credit.model.CreditAccount;
import com.sjs.marketing.domain.credit.model.CreditLedger;
import com.sjs.marketing.domain.credit.service.ICreditService;
import com.sjs.marketing.domain.rebate.model.entity.BehaviorEntity;
import com.sjs.marketing.domain.rebate.model.valobj.BehaviorTypeVO;
import com.sjs.marketing.domain.rebate.service.IBehaviorRebateService;
import com.sjs.marketing.types.exception.AppException;
import com.sjs.marketing.trigger.auth.ApiKeyAuthenticationFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Date;
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

    @Resource
    private IBehaviorRebateService behaviorRebateService;

    @Resource
    private ICreditService creditService;

    private static final SimpleDateFormat DAY_FORMAT = new SimpleDateFormat("yyyyMMdd");

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
     * 每日签到：由上游业务方调用，按「用户 + 自然日」幂等。
     *
     * <p>签到属于上游的场景（用户在上游 App 里签），所以开在开放接口而不是 C 端接口——
     * C 端接口要求终端用户的 JWT，而这里是系统对系统的调用。</p>
     *
     * <p>重复签到不算失败：返回 {@code signed=false}，上游按「今天已签」处理即可。</p>
     */
    @PostMapping("/sign")
    public ResponseEntity<Map<String, Object>> sign(
            @RequestAttribute(name = ApiKeyAuthenticationFilter.ATTR_CLIENT, required = false) String client,
            @RequestBody SignRequest request) {

        if (request.getUserId() == null || request.getUserId().isBlank()) {
            return ResponseEntity.badRequest().body(fail("400", "userId 必填"));
        }

        BehaviorEntity behavior = new BehaviorEntity();
        behavior.setUserId(request.getUserId());
        behavior.setBehaviorTypeVO(BehaviorTypeVO.SIGN);
        behavior.setOutBusinessNo(DAY_FORMAT.format(new Date()));

        try {
            List<String> orderIds = behaviorRebateService.createOrder(behavior);
            log.info("开放接口签到成功 client: {} userId: {} orderIds: {}", client, request.getUserId(), orderIds);

            Map<String, Object> body = ok();
            body.put("userId", request.getUserId());
            body.put("signed", true);
            body.put("orderIds", orderIds);
            return ResponseEntity.ok(body);
        } catch (AppException e) {
            // 返利流水按「用户 + 类型 + 业务单号」唯一，重复签到会命中唯一键
            if ("0003".equals(e.getCode()) || "唯一索引冲突".equals(e.getInfo())) {
                Map<String, Object> body = ok();
                body.put("userId", request.getUserId());
                body.put("signed", false);
                body.put("reason", "今日已签到");
                return ResponseEntity.ok(body);
            }
            log.error("开放接口签到失败 client: {} userId: {}", client, request.getUserId(), e);
            return ResponseEntity.status(500).body(fail(e.getCode(), e.getInfo()));
        } catch (Exception e) {
            log.error("开放接口签到失败 client: {} userId: {}", client, request.getUserId(), e);
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

    /**
     * 权益发放：上游把「业务单号 + 数量」换成用户的积分/次数。
     *
     * <p>{@code eventId} 是幂等键——同一笔业务重复调用只入账一次，
     * 重复时返回 {@code granted=false} 而不是报错（上游按成功处理即可）。</p>
     */
    @PostMapping("/credit/grant")
    public ResponseEntity<Map<String, Object>> grant(
            @RequestAttribute(name = ApiKeyAuthenticationFilter.ATTR_CLIENT, required = false) String client,
            @RequestBody CreditGrantRequest request) {

        if (request.getUserId() == null || request.getUserId().isBlank()
                || request.getEventId() == null || request.getEventId().isBlank()
                || request.getScene() == null || request.getScene().isBlank()
                || request.getAmount() == null || request.getAmount() <= 0) {
            return ResponseEntity.badRequest().body(fail("400", "userId / scene / amount / eventId 均为必填且 amount > 0"));
        }

        try {
            boolean granted = creditService.grant(request.getUserId(), request.getScene(),
                    request.getAmount(), request.getEventId(), request.getTtlDays());
            CreditAccount account = creditService.queryAccount(request.getUserId());
            log.info("权益发放 client:{} userId:{} scene:{} amount:{} eventId:{} granted:{}",
                    client, request.getUserId(), request.getScene(), request.getAmount(), request.getEventId(), granted);

            Map<String, Object> body = ok();
            body.put("userId", request.getUserId());
            body.put("eventId", request.getEventId());
            body.put("granted", granted);
            body.put("balance", account.getBalance());
            return ResponseEntity.ok(body);
        } catch (AppException e) {
            return ResponseEntity.status(400).body(fail(e.getCode(), e.getInfo()));
        } catch (Exception e) {
            log.error("权益发放失败 userId:{} eventId:{}", request.getUserId(), request.getEventId(), e);
            return ResponseEntity.status(500).body(fail("500", e.getMessage()));
        }
    }

    /**
     * 权益消耗：FIFO 扣减（先花掉快过期的批次）。
     *
     * <p>{@code bizNo} 是业务幂等号，同一笔业务重复调用只扣一次。</p>
     */
    @PostMapping("/credit/consume")
    public ResponseEntity<Map<String, Object>> consume(
            @RequestAttribute(name = ApiKeyAuthenticationFilter.ATTR_CLIENT, required = false) String client,
            @RequestBody CreditConsumeRequest request) {

        if (request.getUserId() == null || request.getUserId().isBlank()
                || request.getScene() == null || request.getScene().isBlank()
                || request.getAmount() == null || request.getAmount() <= 0
                || request.getBizNo() == null || request.getBizNo().isBlank()) {
            return ResponseEntity.badRequest().body(fail("400", "userId / scene / amount / bizNo 均为必填且 amount > 0"));
        }

        try {
            int balance = creditService.consume(request.getUserId(), request.getScene(),
                    request.getAmount(), request.getBizNo());
            log.info("权益消耗 client:{} userId:{} scene:{} amount:{} bizNo:{} balance:{}",
                    client, request.getUserId(), request.getScene(), request.getAmount(), request.getBizNo(), balance);

            Map<String, Object> body = ok();
            body.put("userId", request.getUserId());
            body.put("consume", request.getAmount());
            body.put("balance", balance);
            return ResponseEntity.ok(body);
        } catch (AppException e) {
            // 余额不足是业务状态，用 200 + code 表达，避免上游当成系统故障重试
            Map<String, Object> body = fail(e.getCode(), e.getInfo());
            body.put("balance", creditService.queryAccount(request.getUserId()).getBalance());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("权益消耗失败 userId:{} bizNo:{}", request.getUserId(), request.getBizNo(), e);
            return ResponseEntity.status(500).body(fail("500", e.getMessage()));
        }
    }

    /**
     * 查询余额。
     */
    @GetMapping("/credit/balance")
    public ResponseEntity<Map<String, Object>> balance(@RequestParam("userId") String userId) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().body(fail("400", "userId 必填"));
        }
        CreditAccount account = creditService.queryAccount(userId);
        Map<String, Object> body = ok();
        body.put("userId", userId);
        body.put("balance", account.getBalance());
        body.put("frozen", account.getFrozen());
        body.put("totalGranted", account.getTotalGranted());
        body.put("totalConsumed", account.getTotalConsumed());
        body.put("totalExpired", account.getTotalExpired());
        return ResponseEntity.ok(body);
    }

    /**
     * 查询流水（最近的变动）。
     */
    @GetMapping("/credit/records")
    public ResponseEntity<Map<String, Object>> records(@RequestParam("userId") String userId,
                                                       @RequestParam(value = "limit", required = false) Integer limit) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().body(fail("400", "userId 必填"));
        }
        List<CreditLedger> ledgers = creditService.queryLedgers(userId, limit == null ? 50 : limit);
        Map<String, Object> body = ok();
        body.put("userId", userId);
        body.put("records", ledgers);
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
    public static class CreditGrantRequest {
        private String userId;
        private String scene;
        private Integer amount;
        private String eventId;
        private Integer ttlDays;
    }

    @lombok.Data
    public static class CreditConsumeRequest {
        private String userId;
        private String scene;
        private Integer amount;
        private String bizNo;
    }

    @lombok.Data
    public static class SignRequest {
        private String userId;
    }

    @lombok.Data
    public static class RechargeRequest {
        private String userId;
        private Long sku;
        private String outBusinessNo;
    }

}
