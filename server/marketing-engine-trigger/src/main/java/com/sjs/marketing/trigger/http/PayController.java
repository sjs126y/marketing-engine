package com.sjs.marketing.trigger.http;

import com.sjs.marketing.domain.pay.model.PayOrder;
import com.sjs.marketing.domain.pay.model.PayProduct;
import com.sjs.marketing.domain.pay.service.IPayService;
import com.sjs.marketing.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付开放接口（上游业务方调用）。
 *
 * <p>三个入口：下单、支付回调（模拟）、查单。回调也走 API Key + HMAC，
 * 是为了演示「验签 → 幂等 → 状态机」这条链；真接微信支付时换成平台的签名校验即可。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@RestController
@RequestMapping("/open/v1/pay")
public class PayController {

    @Resource
    private IPayService payService;

    /**
     * 下单：上游用「业务单号」换一张待支付订单。
     *
     * <p>同一 {@code orderNo} 重复调用返回同一张单（幂等），不会重复下单。</p>
     */
    @PostMapping("/order")
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody PayOrderRequest request) {
        if (request.getUserId() == null || request.getUserId().isBlank()
                || request.getOrderNo() == null || request.getOrderNo().isBlank()
                || request.getSku() == null || request.getSku().isBlank()) {
            return ResponseEntity.badRequest().body(fail("400", "userId / sku / orderNo 均为必填"));
        }
        try {
            PayOrder order = payService.createOrder(request.getUserId(), request.getSku(), request.getOrderNo());
            Map<String, Object> body = ok();
            body.put("orderNo", order.getOrderNo());
            body.put("state", order.getState());
            body.put("sku", order.getSku());
            body.put("amount", order.getAmount());
            body.put("amountCent", order.getAmountCent());
            return ResponseEntity.ok(body);
        } catch (AppException e) {
            return ResponseEntity.badRequest().body(fail(e.getCode(), e.getInfo()));
        } catch (Exception e) {
            log.error("支付下单失败 userId:{} orderNo:{}", request.getUserId(), request.getOrderNo(), e);
            return ResponseEntity.status(500).body(fail("500", e.getMessage()));
        }
    }

    /**
     * 支付回调（模拟支付平台）。
     *
     * <p>幂等三层：{@code out_trade_no} 唯一（日志表）、订单状态条件更新、发放 eventId。
     * 重复通知返回成功，不重复发权益。</p>
     */
    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> callback(@RequestBody PayCallbackRequest request) {
        if (request.getOutTradeNo() == null || request.getOutTradeNo().isBlank()
                || request.getOrderNo() == null || request.getOrderNo().isBlank()
                || request.getAmountCent() == null) {
            return ResponseEntity.badRequest().body(fail("400", "outTradeNo / orderNo / amountCent 均为必填"));
        }
        try {
            IPayService.CallbackResult result = payService.handleCallback(
                    request.getOutTradeNo(), request.getOrderNo(), request.getAmountCent(),
                    request.getNotifySeq() == null ? 1 : request.getNotifySeq(),
                    request.getRaw() == null ? "{}" : request.getRaw());

            Map<String, Object> body = ok();
            body.put("accepted", result.accepted());
            body.put("duplicated", result.duplicated());
            body.put("state", result.state());
            body.put("reason", result.reason());
            return ResponseEntity.ok(body);
        } catch (AppException e) {
            return ResponseEntity.badRequest().body(fail(e.getCode(), e.getInfo()));
        } catch (Exception e) {
            log.error("支付回调处理失败 orderNo:{} outTradeNo:{}", request.getOrderNo(), request.getOutTradeNo(), e);
            return ResponseEntity.status(500).body(fail("500", e.getMessage()));
        }
    }

    /** 查单：支付状态 + 权益发放状态 */
    @GetMapping("/order")
    public ResponseEntity<Map<String, Object>> queryOrder(@RequestParam("orderNo") String orderNo,
                                                          @RequestParam("userId") String userId) {
        PayOrder order = payService.queryOrder(orderNo, userId);
        if (order == null) {
            return ResponseEntity.status(404).body(fail("404", "订单不存在"));
        }
        Map<String, Object> body = ok();
        body.put("orderNo", order.getOrderNo());
        body.put("state", order.getState());
        body.put("amount", order.getAmount());
        body.put("amountCent", order.getAmountCent());
        body.put("outTradeNo", order.getOutTradeNo());
        body.put("paidAt", order.getPaidAt());
        body.put("grantedAt", order.getGrantedAt());
        return ResponseEntity.ok(body);
    }

    /** 商品目录：页面用它渲染购买入口，避免前端硬编码价格 */
    @GetMapping("/products")
    public ResponseEntity<Map<String, Object>> products() {
        Map<String, Object> body = ok();
        Map<String, Object> items = new LinkedHashMap<>();
        PayProduct.catalog().forEach((sku, product) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sku", sku);
            item.put("type", product.getType());
            item.put("amount", product.getAmount());
            item.put("amountCent", product.getAmountCent());
            items.put(sku, item);
        });
        body.put("products", items);
        return ResponseEntity.ok(body);
    }

    /** 手动关闭超时订单（演示/联调用，生产由定时任务做） */
    @PostMapping("/close-timeout")
    public ResponseEntity<Map<String, Object>> closeTimeout(@RequestParam(name = "timeoutMinutes", required = false) Integer timeoutMinutes) {
        int closed = payService.closeTimeoutOrders(timeoutMinutes == null ? 0 : timeoutMinutes, 200);
        Map<String, Object> body = ok();
        body.put("closed", closed);
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
    public static class PayOrderRequest {
        private String userId;
        private String sku;
        private String orderNo;
    }

    @lombok.Data
    public static class PayCallbackRequest {
        private String outTradeNo;
        private String orderNo;
        private Integer amountCent;
        private Integer notifySeq;
        private String raw;
    }
}
