package com.sjs.marketing.domain.credit.service;

import com.sjs.marketing.domain.credit.adapter.repository.ICreditRepository;
import com.sjs.marketing.domain.credit.model.CreditAccount;
import com.sjs.marketing.domain.credit.model.CreditBalanceVO;
import com.sjs.marketing.domain.credit.model.CreditBatch;
import com.sjs.marketing.domain.credit.model.CreditLedger;
import com.sjs.marketing.domain.credit.model.CreditReconcileDiff;
import com.sjs.marketing.domain.credit.model.CreditReconcileSummary;
import com.sjs.marketing.domain.credit.model.CreditReconcileVO;
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
    private static final Set<String> SCENES = Set.of("sign", "task", "lottery", "invite", "pay", "migrate", "chat", "register");

    /** ttlDays = -1：立即过期。仅用于演示/联调「到期回收」，需要开关打开 */
    private static final int TTL_INSTANT_EXPIRE = -1;

    /** 全量对账的翻页上限：防止游标逻辑写错时无限循环 */
    private static final int MAX_RECONCILE_PAGES = 5000;

    @Resource
    private ICreditRepository creditRepository;

    /** 默认有效期（天）：积分与次数统一 30 天 */
    @Value("${app.credit.default-ttl-days:30}")
    private int defaultTtlDays;

    /** 即将过期提醒窗口（天） */
    @Value("${app.credit.expiring-soon-days:7}")
    private int expiringSoonDays;

    /** 是否允许 ttlDays = -1 造「立即过期」批次（演示/测试用，生产必须关） */
    @Value("${app.credit.demo-instant-expire:false}")
    private boolean demoInstantExpire;

    @Override
    public boolean grant(String userId, String scene, int amount, String eventId, Integer ttlDays) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(eventId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId / eventId 不能为空");
        }
        if (amount <= 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "发放数量必须大于 0");
        }
        String sceneValue = normalizeScene(scene);

        Date grantedAt = new Date();
        Date expiresAt;
        if (ttlDays != null && ttlDays == TTL_INSTANT_EXPIRE) {
            // 演示路径：现场要看「到期 → 余额减少 → 出现 expire 流水」，等 30 天不现实
            if (!demoInstantExpire) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                        "ttlDays=-1 仅用于演示，需开启 app.credit.demo-instant-expire");
            }
            expiresAt = new Date(grantedAt.getTime() - 1000L);
        } else {
            int ttl = ttlDays == null || ttlDays <= 0 ? defaultTtlDays : ttlDays;
            expiresAt = new Date(grantedAt.getTime() + TimeUnit.DAYS.toMillis(ttl));
        }

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

    @Override
    public int expireDueBetween(Date from, Date to, int limit) {
        if (from == null || to == null || !to.after(from)) {
            return 0;
        }
        int size = limit <= 0 || limit > 500 ? 200 : limit;
        List<CreditBatch> due = creditRepository.queryExpireDueBetween(from, to, size);
        int expired = 0;
        for (CreditBatch batch : due) {
            if (creditRepository.expire(batch)) {
                expired++;
            }
        }
        return expired;
    }

    @Override
    public int expireByWindow(int lookbackDays, int batchSize, int maxBatches) {
        int size = batchSize <= 0 || batchSize > 500 ? 200 : batchSize;
        int budget = maxBatches <= 0 ? 2000 : maxBatches;
        int total = 0;
        for (CreditExpireWindow.Slot slot : CreditExpireWindow.slots(new Date(), lookbackDays)) {
            if (total >= budget) {
                break;
            }
            total += drain(slot, size, budget - total);
        }
        if (total < budget) {
            // 兜底：比回看窗口更早的遗留（长时间停机、手工改数）一次补齐
            total += drainAll(size, budget - total);
        }
        if (total > 0) {
            log.info("权益过期回收完成，本次回收 {} 个批次（回看 {} 天）", total, lookbackDays);
        }
        return total;
    }

    /** 把一段窗口内的到期批次全部回收（一次查不完就继续查，直到查空） */
    private int drain(CreditExpireWindow.Slot slot, int size, int budget) {
        if (slot.isEmpty() || budget <= 0) {
            return 0;
        }
        int reclaimed = 0;
        while (reclaimed < budget) {
            int take = Math.min(size, budget - reclaimed);
            int count = expireDueBetween(slot.from(), slot.to(), take);
            if (count <= 0) {
                break;
            }
            reclaimed += count;
        }
        return reclaimed;
    }

    /** 兜底扫描：{@code expires_at <= now} 的全部遗留批次 */
    private int drainAll(int size, int budget) {
        int reclaimed = 0;
        while (reclaimed < budget) {
            int count = expireDue(Math.min(size, budget - reclaimed));
            if (count <= 0) {
                break;
            }
            reclaimed += count;
        }
        return reclaimed;
    }

    @Override
    public CreditBalanceVO queryBalance(String userId) {
        if (StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId 不能为空");
        }
        CreditAccount account = creditRepository.queryAccount(userId);
        Date now = new Date();
        Date windowEnd = new Date(now.getTime() + TimeUnit.DAYS.toMillis(expiringSoonDays));
        int expiringSoon = creditRepository.sumExpiringRemaining(userId, now, windowEnd);
        Date nearestExpireAt = creditRepository.queryNearestExpire(userId);
        return CreditBalanceVO.of(account, expiringSoon, nearestExpireAt, expiringSoonDays);
    }

    @Override
    public CreditReconcileVO reconcile(String userId) {
        if (StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId 不能为空");
        }
        CreditAccount account = creditRepository.queryAccount(userId);
        int accountBalance = account.getBalance() == null ? 0 : account.getBalance();
        int batchRemaining = creditRepository.sumActiveBatchRemaining(userId);
        int ledgerSum = creditRepository.sumLedgerDelta(userId);
        CreditReconcileVO vo = CreditReconcileVO.of(userId, accountBalance, batchRemaining, ledgerSum);
        if (!Boolean.TRUE.equals(vo.getConsistent())) {
            log.warn("权益对账不一致 userId:{} 账户:{} 批次:{} 流水:{} 原因:{}",
                    userId, accountBalance, batchRemaining, ledgerSum,
                    CreditReconcileDiff.classify(accountBalance, batchRemaining, ledgerSum));
        }
        return vo;
    }

    @Override
    public CreditReconcileSummary reconcileAll(int pageSize) {
        int size = pageSize <= 0 || pageSize > 1000 ? 200 : pageSize;
        long begin = System.currentTimeMillis();
        String runId = "R" + begin;
        int scanned = 0;
        int inconsistent = 0;
        int stored = 0;
        String cursor = "";
        for (int page = 0; page < MAX_RECONCILE_PAGES; page++) {
            List<CreditAccount> accounts = creditRepository.queryAccountsAfter(cursor, size);
            if (accounts == null || accounts.isEmpty()) {
                break;
            }
            for (CreditAccount account : accounts) {
                scanned++;
                CreditReconcileVO vo = reconcile(account.getUserId());
                if (!Boolean.TRUE.equals(vo.getConsistent())) {
                    inconsistent++;
                    try {
                        creditRepository.saveReconcileDiff(CreditReconcileDiff.from(runId, vo));
                        stored++;
                    } catch (Exception e) {
                        // 差异落库失败不能中断整轮对账，否则后面的用户就查不到了
                        log.error("对账差异落库失败 userId:{}", account.getUserId(), e);
                    }
                }
            }
            cursor = accounts.get(accounts.size() - 1).getUserId();
            if (accounts.size() < size) {
                break;
            }
        }
        long cost = System.currentTimeMillis() - begin;
        CreditReconcileSummary summary = CreditReconcileSummary.builder()
                .runId(runId)
                .scanned(scanned)
                .consistent(scanned - inconsistent)
                .inconsistent(inconsistent)
                .stored(stored)
                .costMs(cost)
                .build();
        if (inconsistent > 0) {
            log.error("权益对账完成，发现 {} 个账户不一致 runId:{} 扫描:{} 差异落表:{}",
                    inconsistent, runId, scanned, stored);
        } else {
            log.info("权益对账完成，{} 个账户全部一致 runId:{} 耗时:{}ms", scanned, runId, cost);
        }
        return summary;
    }

    @Override
    public List<CreditReconcileDiff> queryReconcileDiffs(String userId, int limit) {
        int size = limit <= 0 || limit > 200 ? 50 : limit;
        return creditRepository.queryReconcileDiffs(null, StringUtils.isBlank(userId) ? null : userId, size);
    }

    private String normalizeScene(String scene) {
        String value = StringUtils.isBlank(scene) ? "task" : scene.trim().toLowerCase();
        if (!SCENES.contains(value)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "不支持的场景：" + scene);
        }
        return value;
    }
}
