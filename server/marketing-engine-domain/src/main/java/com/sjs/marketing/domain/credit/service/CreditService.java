package com.sjs.marketing.domain.credit.service;

import com.sjs.marketing.domain.credit.adapter.repository.ICreditRepository;
import com.sjs.marketing.domain.credit.model.CreditAccount;
import com.sjs.marketing.domain.credit.model.CreditBatch;
import com.sjs.marketing.domain.credit.model.CreditLedger;
import com.sjs.marketing.types.enums.ResponseCode;
import com.sjs.marketing.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 权益资产服务实现。
 *
 * <p>只做参数校验与有效期计算，资产变动全部交给仓储在一个本地事务里完成。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Service
public class CreditService implements ICreditService {

    /** 允许的来源场景——防止调用方随手写脏 scene */
    private static final Set<String> SCENES = Set.of("sign", "task", "lottery", "invite", "pay", "migrate", "chat");

    @Resource
    private ICreditRepository creditRepository;

    /** 默认有效期（天）：积分与次数统一 30 天 */
    @Value("${app.credit.default-ttl-days:30}")
    private int defaultTtlDays;

    @Override
    public boolean grant(String userId, String scene, int amount, String eventId, Integer ttlDays) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(eventId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId / eventId 不能为空");
        }
        if (amount <= 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "发放数量必须大于 0");
        }
        String sceneValue = normalizeScene(scene);

        int ttl = ttlDays == null || ttlDays <= 0 ? defaultTtlDays : ttlDays;
        Date grantedAt = new Date();
        Date expiresAt = new Date(grantedAt.getTime() + TimeUnit.DAYS.toMillis(ttl));

        boolean inserted = creditRepository.grant(userId, sceneValue, amount, eventId, grantedAt, expiresAt);
        if (!inserted) {
            log.info("权益发放幂等命中，跳过入账 userId:{} scene:{} eventId:{}", userId, sceneValue, eventId);
        } else {
            log.info("权益发放成功 userId:{} scene:{} amount:{} eventId:{} expiresAt:{}",
                    userId, sceneValue, amount, eventId, expiresAt);
        }
        return inserted;
    }

    @Override
    public int consume(String userId, String scene, int amount, String bizNo) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(bizNo)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId / bizNo 不能为空");
        }
        if (amount <= 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "消耗数量必须大于 0");
        }
        String sceneValue = normalizeScene(scene);
        return creditRepository.consume(userId, sceneValue, amount, bizNo);
    }

    @Override
    public CreditAccount queryAccount(String userId) {
        if (StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId 不能为空");
        }
        return creditRepository.queryAccount(userId);
    }

    @Override
    public List<CreditLedger> queryLedgers(String userId, int limit) {
        if (StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId 不能为空");
        }
        int size = limit <= 0 || limit > 200 ? 50 : limit;
        return creditRepository.queryLedgers(userId, size);
    }

    @Override
    public int expireDue(int limit) {
        int size = limit <= 0 || limit > 500 ? 200 : limit;
        List<CreditBatch> due = creditRepository.queryExpireDue(new Date(), size);
        int expired = 0;
        for (CreditBatch batch : due) {
            if (creditRepository.expire(batch)) {
                expired++;
            }
        }
        if (expired > 0) {
            log.info("权益过期回收完成，本次回收 {} 个批次", expired);
        }
        return expired;
    }

    private String normalizeScene(String scene) {
        String value = StringUtils.isBlank(scene) ? "task" : scene.trim().toLowerCase();
        if (!SCENES.contains(value)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "不支持的场景：" + scene);
        }
        return value;
    }
}
