package com.sjs.marketing.trigger.http;

import com.sjs.marketing.domain.award.model.valobj.UserAwardRecordVO;
import com.sjs.marketing.domain.award.service.IAwardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营端发奖记录。
 *
 * <p>这块数据直接暴露了当前链路的一个缺口：{@code awardState} 一直是 {@code create}，
 * 因为发奖只做到「落库 + 消息投递 + 消费回执」，还没有真正的发奖渠道把状态推进到
 * {@code complete}。下游交付能力补上后，这一列才会真正流动起来。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@RestController
@RequestMapping("/console/v1/awards")
public class ConsoleAwardController {

    @Resource
    private IAwardService awardService;

    @GetMapping("/recent")
    public ResponseEntity<Map<String, Object>> recent(@RequestParam(defaultValue = "20") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<UserAwardRecordVO> records = awardService.queryRecentAwards(safeLimit);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "0000");
        body.put("info", "成功");
        body.put("count", records.size());
        body.put("records", records);
        return ResponseEntity.ok(body);
    }

}
