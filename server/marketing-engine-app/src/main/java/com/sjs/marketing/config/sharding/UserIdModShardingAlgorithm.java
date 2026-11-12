package com.sjs.marketing.config.sharding;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * 按 user_id 的哈希取模路由。
 *
 * <p>同一个算法同时用于库和表：候选节点集合的大小决定模数——
 * 库候选是 {@code ds1/ds2}（2 个），表候选是 {@code xxx_000 ~ xxx_003}（4 个），
 * 因此取模结果天然落在正确范围内。</p>
 *
 * <p>用 {@code String.hashCode()} 而不是数值解析，是因为 userId 是字符串
 * （如 {@code demo-user}），旧的路由组件也是按 hashCode 取模。</p>
 *
 * @author marketing-engine contributors
 */
public final class UserIdModShardingAlgorithm implements StandardShardingAlgorithm<String> {

    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<String> shardingValue) {
        List<String> targets = new ArrayList<>(availableTargetNames);
        targets.sort(null);
        int index = Math.floorMod(Objects.toString(shardingValue.getValue(), "").hashCode(), targets.size());
        return targets.get(index);
    }

    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames, RangeShardingValue<String> shardingValue) {
        // 范围查询没有精确分片键，路由到全部候选分片
        return availableTargetNames;
    }

    @Override
    public void init(Properties props) {
        // 无额外参数
    }

    @Override
    public String getType() {
        return "USER_ID_MOD";
    }

}
