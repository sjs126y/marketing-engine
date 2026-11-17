package com.sjs.marketing.infrastructure.adapter.repository;

import com.sjs.marketing.domain.credit.adapter.repository.ICreditRepository;
import com.sjs.marketing.domain.credit.model.CreditAccount;
import com.sjs.marketing.domain.credit.model.CreditBatch;
import com.sjs.marketing.domain.credit.model.CreditLedger;
import com.sjs.marketing.domain.credit.model.CreditReconcileDiff;
import com.sjs.marketing.domain.credit.service.CreditFifoPlanner;
import com.sjs.marketing.infrastructure.dao.ICreditDao;
import com.sjs.marketing.infrastructure.dao.po.CreditAccountPO;
import com.sjs.marketing.infrastructure.dao.po.CreditBatchPO;
import com.sjs.marketing.infrastructure.dao.po.CreditLedgerPO;
import com.sjs.marketing.infrastructure.dao.po.CreditReconcileDiffPO;
import com.sjs.marketing.types.enums.ResponseCode;
import com.sjs.marketing.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 权益资产仓储实现。
 *
 * <p>三张表同按 {@code user_id} 分库，所以「扣账户 + 扣批次 + 写流水」可以放进
 * 一个本地事务里，不需要分布式事务。</p>
 *
 * <p>幂等分两层：</p>
 * <ul>
 *   <li>{@code credit_batch.event_id} 唯一 → 同一笔发放只入账一次；</li>
 *   <li>{@code credit_ledger.biz_no} 唯一 → 同一笔业务只记一次账。</li>
 * </ul>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Repository
public class CreditRepository implements ICreditRepository {

    @Resource
    private ICreditDao creditDao;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public boolean grant(String userId, String scene, int amount, String eventId, Date grantedAt, Date expiresAt) {
        CreditBatchPO batch = new CreditBatchPO();
        batch.setUserId(userId);
        batch.setEventId(eventId);
        batch.setScene(scene);
        batch.setAmount(amount);
        batch.setRemaining(amount);
        batch.setGrantedAt(grantedAt);
        batch.setExpiresAt(expiresAt);
        batch.setState(CreditBatch.STATE_ACTIVE);

        Boolean inserted = transactionTemplate.execute(status -> {
            // 1. 批次先落库：event_id 唯一索引在这里挡住重复发放
            try {
                creditDao.insertBatch(batch);
            } catch (DuplicateKeyException e) {
                log.info("权益批次已存在，幂等跳过 userId:{} eventId:{}", userId, eventId);
                status.setRollbackOnly();
                return false;
            }

            // 2. 账户累加（首次发放时先建账户）
            ensureAccount(userId);
            creditDao.increaseAccountGrant(userId, amount);
            CreditAccountPO account = creditDao.queryAccount(userId);

            // 3. 记发放流水
            CreditLedgerPO ledger = new CreditLedgerPO();
            ledger.setUserId(userId);
            ledger.setBizNo(eventId);
            ledger.setBatchId(batch.getId());
            ledger.setScene(scene);
            ledger.setType(CreditLedger.TYPE_GRANT);
            ledger.setDelta(amount);
            ledger.setBalanceAfter(account == null ? amount : account.getBalance());
            creditDao.insertLedger(ledger);
            return true;
        });
        return Boolean.TRUE.equals(inserted);
    }

    @Override
    public int consume(String userId, String scene, int amount, String bizNo) {
        Integer balanceAfter = transactionTemplate.execute(status -> {
            // 幂等前置：同一个 bizNo 重放直接返回当前余额，而不是撞唯一键报错
            // （支付回调、上游重试都会重放，报错会让调用方以为失败）
            if (creditDao.countLedgerByBizNo(userId, bizNo) > 0) {
                CreditAccountPO existed = creditDao.queryAccount(userId);
                log.info("权益消耗幂等命中，返回当前余额 userId:{} bizNo:{}", userId, bizNo);
                return existed == null || existed.getBalance() == null ? 0 : existed.getBalance();
            }
            CreditAccountPO account = creditDao.queryAccount(userId);
            if (account == null || account.getBalance() == null || account.getBalance() < amount) {
                throw new AppException(ResponseCode.ACCOUNT_QUOTA_ERROR.getCode(), "权益余额不足");
            }

            // FIFO：领域层计划器按过期时间升序算出「每个批次扣多少」（纯函数，可单测）
            List<CreditBatch> batches = new ArrayList<>();
            for (CreditBatchPO po : creditDao.queryActiveBatches(userId, new Date())) {
                batches.add(CreditBatch.builder()
                        .id(po.getId())
                        .userId(po.getUserId())
                        .eventId(po.getEventId())
                        .scene(po.getScene())
                        .amount(po.getAmount())
                        .remaining(po.getRemaining())
                        .grantedAt(po.getGrantedAt())
                        .expiresAt(po.getExpiresAt())
                        .state(po.getState())
                        .build());
            }

            List<CreditFifoPlanner.Deduction> plan = CreditFifoPlanner.plan(batches, amount);
            if (!CreditFifoPlanner.sufficient(plan, amount)) {
                status.setRollbackOnly();
                throw new AppException(ResponseCode.ACCOUNT_QUOTA_ERROR.getCode(), "权益余额不足（可用批次不足）");
            }

            int running = account.getBalance();
            int index = 0;
            for (CreditFifoPlanner.Deduction deduction : plan) {
                int updated = creditDao.deductBatchRemaining(deduction.batchId(), userId, deduction.take());
                if (updated != 1) {
                    // 并发下该批次已被别的请求扣走，整笔回滚，由调用方重试
                    status.setRollbackOnly();
                    throw new AppException(ResponseCode.ACCOUNT_QUOTA_ERROR.getCode(), "批次扣减冲突，请重试");
                }
                if (deduction.remainingAfter() == 0) {
                    creditDao.markBatchExhausted(deduction.batchId(), userId);
                }

                running -= deduction.take();

                CreditLedgerPO ledger = new CreditLedgerPO();
                ledger.setUserId(userId);
                // 第一条直接用 bizNo：幂等判定只查这一条，能走唯一索引精确命中
                ledger.setBizNo(index == 0 ? bizNo : bizNo + ":" + deduction.batchId());
                index++;
                ledger.setBatchId(deduction.batchId());
                ledger.setScene(scene);
                ledger.setType(CreditLedger.TYPE_CONSUME);
                ledger.setDelta(-deduction.take());
                ledger.setBalanceAfter(running);
                creditDao.insertLedger(ledger);
            }

            // 汇总账户扣减：条件更新，余额不足时 0 行 → 回滚
            if (creditDao.deductAccount(userId, amount) != 1) {
                status.setRollbackOnly();
                throw new AppException(ResponseCode.ACCOUNT_QUOTA_ERROR.getCode(), "账户余额扣减失败");
            }
            return running;
        });

        if (balanceAfter == null) {
            throw new AppException(ResponseCode.ACCOUNT_QUOTA_ERROR.getCode(), "权益消耗失败");
        }
        return balanceAfter;
    }

    @Override
    public CreditAccount queryAccount(String userId) {
        CreditAccountPO po = creditDao.queryAccount(userId);
        if (po == null) {
            return CreditAccount.empty(userId);
        }
        return CreditAccount.builder()
                .userId(po.getUserId())
                .balance(po.getBalance())
                .frozen(po.getFrozen())
                .totalGranted(po.getTotalGranted())
                .totalConsumed(po.getTotalConsumed())
                .totalExpired(po.getTotalExpired())
                .updateTime(po.getUpdateTime())
                .build();
    }

    @Override
    public List<CreditLedger> queryLedgers(String userId, int limit) {
        List<CreditLedgerPO> rows = creditDao.queryLedgers(userId, limit);
        List<CreditLedger> result = new ArrayList<>(rows.size());
        for (CreditLedgerPO po : rows) {
            result.add(CreditLedger.builder()
                    .id(po.getId())
                    .userId(po.getUserId())
                    .bizNo(po.getBizNo())
                    .batchId(po.getBatchId())
                    .scene(po.getScene())
                    .type(po.getType())
                    .delta(po.getDelta())
                    .balanceAfter(po.getBalanceAfter())
                    .createTime(po.getCreateTime())
                    .build());
        }
        return result;
    }

    @Override
    public List<CreditBatch> queryExpireDue(Date now, int limit) {
        List<CreditBatchPO> rows = creditDao.queryExpireDue(now, limit);
        List<CreditBatch> result = new ArrayList<>(rows.size());
        for (CreditBatchPO po : rows) {
            result.add(CreditBatch.builder()
                    .id(po.getId())
                    .userId(po.getUserId())
                    .eventId(po.getEventId())
                    .scene(po.getScene())
                    .amount(po.getAmount())
                    .remaining(po.getRemaining())
                    .grantedAt(po.getGrantedAt())
                    .expiresAt(po.getExpiresAt())
                    .state(po.getState())
                    .build());
        }
        return result;
    }

    @Override
    public boolean expire(CreditBatch batch) {
        if (batch.getRemaining() == null || batch.getRemaining() <= 0) {
            // 已用完的批次只需把状态改成过期，不动账户
            return creditDao.markBatchExpired(batch.getId(), batch.getUserId()) == 1;
        }
        Boolean done = transactionTemplate.execute(status -> {
            int updated = creditDao.markBatchExpired(batch.getId(), batch.getUserId());
            if (updated != 1) {
                // 已被别的实例回收
                status.setRollbackOnly();
                return false;
            }
            int reclaimed = creditDao.increaseAccountExpired(batch.getUserId(), batch.getRemaining());
            if (reclaimed != 1) {
                status.setRollbackOnly();
                log.error("过期回收失败，账户余额不足 userId:{} batchId:{}", batch.getUserId(), batch.getId());
                return false;
            }
            CreditLedgerPO ledger = new CreditLedgerPO();
            ledger.setUserId(batch.getUserId());
            ledger.setBizNo("expire:" + batch.getId());
            ledger.setBatchId(batch.getId());
            ledger.setScene(batch.getScene());
            ledger.setType(CreditLedger.TYPE_EXPIRE);
            ledger.setDelta(-batch.getRemaining());
            // balance_after 是 NOT NULL：回收后账户已经扣减，取当前余额写进流水
            CreditAccountPO account = creditDao.queryAccount(batch.getUserId());
            ledger.setBalanceAfter(account == null || account.getBalance() == null ? 0 : account.getBalance());
            creditDao.insertLedger(ledger);
            return true;
        });
        return Boolean.TRUE.equals(done);
    }

    /** 首次发放时建账户；并发下靠唯一键兜底 */
    private void ensureAccount(String userId) {
        if (creditDao.queryAccount(userId) != null) {
            return;
        }
        CreditAccountPO account = new CreditAccountPO();
        account.setUserId(userId);
        account.setBalance(0);
        account.setFrozen(0);
        account.setTotalGranted(0);
        account.setTotalConsumed(0);
        account.setTotalExpired(0);
        try {
            creditDao.insertAccount(account);
        } catch (DuplicateKeyException ignore) {
            // 并发下另一个请求已建好，直接用
        }
    }

    @Override
    public List<CreditBatch> queryExpireDueBetween(Date from, Date to, int limit) {
        List<CreditBatchPO> rows = creditDao.queryExpireDueBetween(from, to, limit);
        List<CreditBatch> result = new ArrayList<>(rows.size());
        for (CreditBatchPO po : rows) {
            result.add(CreditBatch.builder()
                    .id(po.getId())
                    .userId(po.getUserId())
                    .eventId(po.getEventId())
                    .scene(po.getScene())
                    .amount(po.getAmount())
                    .remaining(po.getRemaining())
                    .grantedAt(po.getGrantedAt())
                    .expiresAt(po.getExpiresAt())
                    .state(po.getState())
                    .build());
        }
        return result;
    }

    @Override
    public int sumActiveBatchRemaining(String userId) {
        Integer value = creditDao.sumActiveRemaining(userId);
        return value == null ? 0 : value;
    }

    @Override
    public int sumLedgerDelta(String userId) {
        Integer value = creditDao.sumLedgerDelta(userId);
        return value == null ? 0 : value;
    }

    @Override
    public int sumExpiringRemaining(String userId, Date from, Date to) {
        Integer value = creditDao.sumExpiringRemaining(userId, from, to);
        return value == null ? 0 : value;
    }

    @Override
    public Date queryNearestExpire(String userId) {
        return creditDao.queryNearestExpire(userId);
    }

    @Override
    public List<CreditAccount> queryAccountsAfter(String afterUserId, int limit) {
        List<CreditAccountPO> rows = creditDao.queryAccountsAfter(afterUserId == null ? "" : afterUserId, limit);
        List<CreditAccount> result = new ArrayList<>(rows.size());
        for (CreditAccountPO po : rows) {
            result.add(toAccount(po));
        }
        return result;
    }

    @Override
    public void saveReconcileDiff(CreditReconcileDiff diff) {
        CreditReconcileDiffPO po = new CreditReconcileDiffPO();
        po.setRunId(diff.getRunId());
        po.setUserId(diff.getUserId());
        po.setAccountBalance(diff.getAccountBalance());
        po.setBatchRemaining(diff.getBatchRemaining());
        po.setLedgerSum(diff.getLedgerSum());
        po.setDiff(diff.getDiff());
        po.setReason(diff.getReason());
        po.setState(diff.getState());
        creditDao.insertReconcileDiff(po);
    }

    @Override
    public List<CreditReconcileDiff> queryReconcileDiffs(String runId, String userId, int limit) {
        List<CreditReconcileDiffPO> rows = creditDao.queryReconcileDiffs(runId, userId, limit);
        List<CreditReconcileDiff> result = new ArrayList<>(rows.size());
        for (CreditReconcileDiffPO po : rows) {
            result.add(CreditReconcileDiff.builder()
                    .id(po.getId())
                    .runId(po.getRunId())
                    .userId(po.getUserId())
                    .accountBalance(po.getAccountBalance())
                    .batchRemaining(po.getBatchRemaining())
                    .ledgerSum(po.getLedgerSum())
                    .diff(po.getDiff())
                    .reason(po.getReason())
                    .state(po.getState())
                    .createTime(po.getCreateTime())
                    .build());
        }
        return result;
    }

    @Override
    public int sumLedgerByBizNo(String userId, String bizNo) {
        Integer value = creditDao.sumLedgerByBizNo(userId, bizNo);
        return value == null ? 0 : value;
    }

    @Override
    public int reverseGrant(String userId, String eventId, String bizNo) {
        Integer reversed = transactionTemplate.execute(status -> {
            CreditBatchPO batch = creditDao.queryBatchByEventId(userId, eventId);
            if (batch == null || batch.getRemaining() == null || batch.getRemaining() <= 0) {
                return 0;
            }
            int remaining = batch.getRemaining();
            if (creditDao.markBatchReversed(batch.getId(), userId) != 1) {
                // 抢不到：已被别处冲正
                status.setRollbackOnly();
                return 0;
            }
            if (creditDao.reverseAccountBalance(userId, remaining) != 1) {
                status.setRollbackOnly();
                log.error("退款冲正失败，账户余额不足 userId:{} batchId:{}", userId, batch.getId());
                return 0;
            }
            CreditAccountPO account = creditDao.queryAccount(userId);
            CreditLedgerPO ledger = new CreditLedgerPO();
            ledger.setUserId(userId);
            ledger.setBizNo(bizNo);
            ledger.setBatchId(batch.getId());
            ledger.setScene("pay");
            ledger.setType(CreditLedger.TYPE_REVERSE);
            ledger.setDelta(-remaining);
            ledger.setBalanceAfter(account == null || account.getBalance() == null ? 0 : account.getBalance());
            creditDao.insertLedger(ledger);
            return remaining;
        });
        return reversed == null ? 0 : reversed;
    }

    private CreditAccount toAccount(CreditAccountPO po) {
        return CreditAccount.builder()
                .userId(po.getUserId())
                .balance(po.getBalance())
                .frozen(po.getFrozen())
                .totalGranted(po.getTotalGranted())
                .totalConsumed(po.getTotalConsumed())
                .totalExpired(po.getTotalExpired())
                .updateTime(po.getUpdateTime())
                .build();
    }
}
