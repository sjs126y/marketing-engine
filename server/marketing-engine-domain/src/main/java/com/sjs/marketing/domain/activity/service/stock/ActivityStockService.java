package com.sjs.marketing.domain.activity.service.stock;

import com.sjs.marketing.domain.activity.adapter.repository.IActivityRepository;
import com.sjs.marketing.domain.activity.model.valobj.ActivitySkuStockReconcileVO;
import com.sjs.marketing.domain.activity.model.valobj.ActivitySkuStockSnapshotVO;
import com.sjs.marketing.domain.activity.service.IActivityStockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * SKU 库存回写服务。
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Service
public class ActivityStockService implements IActivityStockService {

    @Resource
    private IActivityRepository repository;

    @Override
    public void applyConsumed(Long sku, Integer consumedAfter) {
        if (sku == null || consumedAfter == null) {
            return;
        }
        int updated = repository.updateAppliedConsumed(sku, consumedAfter);
        if (updated == 0) {
            log.debug("SKU 库存回写跳过（已有不小于该值的记录或 SKU 不存在） sku: {} consumedAfter: {}", sku, consumedAfter);
        }
    }

    @Override
    public List<ActivitySkuStockSnapshotVO> queryStockSnapshot() {
        return repository.querySkuStockSnapshot();
    }

    @Override
    public List<ActivitySkuStockReconcileVO> reconcile() {
        List<ActivitySkuStockSnapshotVO> snapshots = repository.querySkuStockSnapshot();
        List<ActivitySkuStockReconcileVO> result = new ArrayList<>(snapshots.size());

        for (ActivitySkuStockSnapshotVO snapshot : snapshots) {
            Integer redisSurplus = repository.querySkuSurplusFromRedis(snapshot.getSku());
            if (redisSurplus == null) {
                result.add(ActivitySkuStockReconcileVO.builder()
                        .sku(snapshot.getSku())
                        .stockCount(snapshot.getStockCount())
                        .dbConsumed(snapshot.consumedByDB())
                        .skipped(true)
                        .build());
                continue;
            }

            int total = snapshot.getStockCount() == null ? 0 : snapshot.getStockCount();
            result.add(ActivitySkuStockReconcileVO.builder()
                    .sku(snapshot.getSku())
                    .stockCount(total)
                    .redisConsumed(total - redisSurplus)
                    .dbConsumed(snapshot.consumedByDB())
                    .skipped(false)
                    .build());
        }

        return result;
    }
}
