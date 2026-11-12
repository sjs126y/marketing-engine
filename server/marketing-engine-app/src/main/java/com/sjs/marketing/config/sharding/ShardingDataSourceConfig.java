package com.sjs.marketing.config.sharding;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.algorithm.core.config.AlgorithmConfiguration;
import org.apache.shardingsphere.infra.config.rule.RuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.NoneShardingStrategyConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;
import org.apache.shardingsphere.single.config.SingleRuleConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 分库分表配置。
 *
 * <p>拓扑：</p>
 * <pre>
 *   ds0  ── 配置库：活动、策略、奖品、规则树
 *   ds1  ┐
 *   ds2  ┘ 分片库：按 user_id 哈希取模
 *          分表：订单/抽奖单/中奖记录/返利单 各 4 张
 *          分库不分表：账户、账户月、账户日、task
 * </pre>
 *
 * @author marketing-engine contributors
 */
@Configuration
public class ShardingDataSourceConfig {

    private static final String SHARDING_COLUMN = "user_id";

    private static final String ALGORITHM_NAME = "user-id-mod";

    /** 只在 ds0 的配置类表，不需要分片。 */
    private static final List<String> SINGLE_TABLES = List.of(
            "raffle_activity",
            "raffle_activity_count",
            "raffle_activity_sku",
            "strategy",
            "strategy_rule",
            "strategy_award",
            "award",
            "rule_tree",
            "rule_tree_node",
            "rule_tree_node_line");

    /** 分库 + 分表：订单类，每个库 4 张。 */
    private static final List<String> SHARDED_TABLES = List.of(
            "raffle_activity_order",
            "user_raffle_order",
            "user_award_record",
            "user_behavior_rebate_order");

    /** 只分库不分表：同一张表在 ds1/ds2 各一份。 */
    private static final List<String> DB_ONLY_TABLES = List.of(
            "raffle_activity_account",
            "raffle_activity_account_month",
            "raffle_activity_account_day",
            "task");

    @Bean
    public DataSource dataSource(ShardingDataSourceProperties properties) throws SQLException {
        Map<String, DataSource> dataSources = new LinkedHashMap<>();
        dataSources.put("ds0", createDataSource(properties.getDb0()));
        dataSources.put("ds1", createDataSource(properties.getDb1()));
        dataSources.put("ds2", createDataSource(properties.getDb2()));

        ShardingRuleConfiguration sharding = new ShardingRuleConfiguration();
        sharding.getShardingAlgorithms().put(ALGORITHM_NAME, createAlgorithm());

        for (String table : SHARDED_TABLES) {
            ShardingTableRuleConfiguration rule = new ShardingTableRuleConfiguration(
                    table, "ds$->{1..2}." + table + "_$->{0..3}");
            rule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration(SHARDING_COLUMN, ALGORITHM_NAME));
            rule.setTableShardingStrategy(new StandardShardingStrategyConfiguration(SHARDING_COLUMN, ALGORITHM_NAME));
            sharding.getTables().add(rule);
        }

        for (String table : DB_ONLY_TABLES) {
            ShardingTableRuleConfiguration rule = new ShardingTableRuleConfiguration(
                    table, "ds$->{1..2}." + table);
            rule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration(SHARDING_COLUMN, ALGORITHM_NAME));
            rule.setTableShardingStrategy(new NoneShardingStrategyConfiguration());
            sharding.getTables().add(rule);
        }

        SingleRuleConfiguration single = new SingleRuleConfiguration();
        List<String> singleTables = new ArrayList<>();
        for (String table : SINGLE_TABLES) {
            singleTables.add("ds0." + table);
        }
        single.setTables(singleTables);
        single.setDefaultDataSource("ds0");

        List<RuleConfiguration> rules = List.of(sharding, single);
        return ShardingSphereDataSourceFactory.createDataSource(dataSources, rules, new Properties());
    }

    private AlgorithmConfiguration createAlgorithm() {
        Properties props = new Properties();
        props.setProperty("strategy", "STANDARD");
        props.setProperty("algorithmClassName", UserIdModShardingAlgorithm.class.getName());
        return new AlgorithmConfiguration("CLASS_BASED", props);
    }

    private DataSource createDataSource(ShardingDataSourceProperties.Db db) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(db.getUrl());
        config.setUsername(db.getUsername());
        config.setPassword(db.getPassword());
        config.setDriverClassName(db.getDriverClassName());
        config.setMaximumPoolSize(db.getMaximumPoolSize());
        config.setMinimumIdle(db.getMinimumIdle());
        config.setPoolName("hikari-" + System.nanoTime());
        return new HikariDataSource(config);
    }

}
