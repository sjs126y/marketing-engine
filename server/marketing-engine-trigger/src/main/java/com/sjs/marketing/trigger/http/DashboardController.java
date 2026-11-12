package com.sjs.marketing.trigger.http;

import com.sjs.marketing.domain.activity.model.valobj.ActivitySkuStockReconcileVO;
import com.sjs.marketing.domain.activity.service.IActivityStockService;
import com.sjs.marketing.domain.outbox.service.IOutboxAckService;
import com.sjs.marketing.domain.strategy.adapter.repository.IStrategyRepository;
import com.sjs.marketing.domain.strategy.model.entity.StrategyAwardEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营看板。
 *
 * <p>三块数据都能直接回答「系统现在健不健康」：</p>
 * <ul>
 *   <li>库存水位：Redis 与数据库已扣数的偏差，判断回写有没有丢；</li>
 *   <li>消息积压：outbox 待投递条数，判断 MQ 链路是否卡住；</li>
 *   <li>概率分布：配置概率，配合中奖记录可以算实际分布。</li>
 * </ul>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@RestController
@RequestMapping("/console/v1/dashboard")
public class DashboardController {

    @Resource
    private IActivityStockService activityStockService;

    @Resource
    private IOutboxAckService outboxAckService;

    @Resource
    private IStrategyRepository strategyRepository;

    /**
     * 总览：库存对账 + 消息积压 + 指定策略的概率配置。
     */
    @PostMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview(@RequestParam(required = false) Long strategyId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "0000");
        body.put("info", "成功");

        // 1. 库存水位
        List<ActivitySkuStockReconcileVO> stocks = activityStockService.reconcile();
        long inconsistent = stocks.stream().filter(v -> v.inconsistent(3)).count();
        Map<String, Object> stockBlock = new LinkedHashMap<>();
        stockBlock.put("skuCount", stocks.size());
        stockBlock.put("inconsistentCount", inconsistent);
        stockBlock.put("detail", stocks);
        body.put("stock", stockBlock);

        // 2. 消息积压
        Map<String, Object> outboxBlock = new LinkedHashMap<>();
        outboxBlock.put("pending", outboxAckService.pendingCount());
        body.put("outbox", outboxBlock);

        // 3. 概率配置
        if (strategyId != null) {
            List<Map<String, Object>> awards = new ArrayList<>();
            for (StrategyAwardEntity award : strategyRepository.queryStrategyAwardList(strategyId)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("awardId", award.getAwardId());
                item.put("awardTitle", award.getAwardTitle());
                item.put("awardRate", award.getAwardRate());
                item.put("awardCount", award.getAwardCount());
                item.put("awardCountSurplus", award.getAwardCountSurplus());
                awards.add(item);
            }
            body.put("strategyId", strategyId);
            body.put("awards", awards);
        }

        return ResponseEntity.ok(body);
    }

}
