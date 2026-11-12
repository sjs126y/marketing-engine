package com.sjs.marketing.config.sharding;

import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.driver.api.yaml.YamlShardingSphereDataSourceFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;

/**
 * 分库分表配置。
 *
 * <p>拓扑（与原自研路由组件保持一致）：</p>
 * <pre>
 *   ds0  ── 配置库：活动、策略、奖品、规则树
 *   ds1  ┐
 *   ds2  ┘ 分片库：按 user_id 哈希取模
 *          分表：订单 / 抽奖单 / 中奖记录 / 返利单，各 4 张
 *          分库不分表：账户、账户月、账户日、outbox、task
 * </pre>
 *
 * <p>用官方 YAML 入口构造：ShardingSphere 5.5 在「从 DataSource 对象反推连接属性」那条路径上
 * 对 HikariDataSource 支持不佳（会因取不到 url 而 NPE），走 YAML 由它自己创建连接池更稳，
 * 也把分片规则变成可以直接对照文档的声明式配置。</p>
 *
 * @author marketing-engine contributors
 */
@Slf4j
@Configuration
public class ShardingDataSourceConfig {

    /** 分片键 */
    private static final String SHARDING_COLUMN = "user_id";

    /** 分片算法名 */
    private static final String ALGORITHM_NAME = "user_id_mod";

    /** 只在 ds0 的配置类表 */
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
            "rule_tree_node_line",
            // 日常行为返利配置：签到等行为触发返利时查询
            "daily_behavior_rebate");

    /** 分库 + 分表：订单类，每个库 4 张 */
    private static final List<String> SHARDED_TABLES = List.of(
            "raffle_activity_order",
            "user_raffle_order",
            "user_award_record",
            "user_behavior_rebate_order");

    /** 只分库不分表 */
    private static final List<String> DB_ONLY_TABLES = List.of(
            "raffle_activity_account",
            "raffle_activity_account_month",
            "raffle_activity_account_day",
            "outbox",
            // task 表保留历史数据，新链路不再写入
            "task");

    @Bean
    public DataSource dataSource(ShardingDataSourceProperties properties) throws SQLException, IOException {
        String yaml = buildYaml(properties);
        log.info("初始化 ShardingSphere 数据源：{} 张分片表、{} 张分库表、{} 张单库表",
                SHARDED_TABLES.size(), DB_ONLY_TABLES.size(), SINGLE_TABLES.size());
        return YamlShardingSphereDataSourceFactory.createDataSource(yaml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 ShardingSphere YAML 配置。
     *
     * <p>生成而非写死在资源文件里，是为了让账号密码继续从环境变量注入。</p>
     */
    private String buildYaml(ShardingDataSourceProperties properties) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("databaseName: marketing_engine\n");
        // 单机模式：分片元数据放内存，不额外落库，避免引入额外的元数据表
        yaml.append("mode:\n");
        yaml.append("  type: Standalone\n");
        yaml.append("  repository:\n");
        yaml.append("    type: Memory\n");
        yaml.append("dataSources:\n");
        appendDataSource(yaml, "ds0", properties.getDb0());
        appendDataSource(yaml, "ds1", properties.getDb1());
        appendDataSource(yaml, "ds2", properties.getDb2());

        yaml.append("rules:\n");
        yaml.append("  - !SHARDING\n");
        yaml.append("    tables:\n");
        for (String table : SHARDED_TABLES) {
            yaml.append("      ").append(table).append(":\n");
            yaml.append("        actualDataNodes: ").append(shardedNodes(table)).append('\n');
            appendStrategy(yaml, "databaseStrategy");
            appendStrategy(yaml, "tableStrategy");
        }
        for (String table : DB_ONLY_TABLES) {
            yaml.append("      ").append(table).append(":\n");
            yaml.append("        actualDataNodes: ds$->{1..2}.").append(table).append('\n');
            appendStrategy(yaml, "databaseStrategy");
            yaml.append("        tableStrategy:\n");
            yaml.append("          none:\n");
        }
        yaml.append("    shardingAlgorithms:\n");
        yaml.append("      ").append(ALGORITHM_NAME).append(":\n");
        yaml.append("        type: CLASS_BASED\n");
        yaml.append("        props:\n");
        yaml.append("          strategy: STANDARD\n");
        yaml.append("          algorithmClassName: ").append(UserIdModShardingAlgorithm.class.getName()).append('\n');

        yaml.append("  - !SINGLE\n");
        yaml.append("    tables:\n");
        for (String table : SINGLE_TABLES) {
            yaml.append("      - ds0.").append(table).append('\n');
        }
        yaml.append("    defaultDataSource: ds0\n");

        return yaml.toString();
    }


    /**
     * 显式列出分表节点。
     *
     * <p>不用 {@code ds$->{1..2}.xxx_$->{0..3}} 这种行内表达式：它展开出的是
     * {@code xxx_0 ~ xxx_3}（一位数），而物理表是 {@code xxx_000 ~ xxx_003}（三位补零），
     * 名称对不上会导致元数据加载时「表不存在」。显式枚举虽然啰嗦，但没有歧义。</p>
     */
    private String shardedNodes(String table) {
        StringBuilder nodes = new StringBuilder();
        for (int db = 1; db <= 2; db++) {
            for (int suffix = 0; suffix <= 3; suffix++) {
                if (nodes.length() > 0) {
                    nodes.append(',');
                }
                nodes.append("ds").append(db).append('.').append(table)
                        .append('_').append(String.format("%03d", suffix));
            }
        }
        return nodes.toString();
    }

    private void appendDataSource(StringBuilder yaml, String name, ShardingDataSourceProperties.Db db) {
        yaml.append("  ").append(name).append(":\n");
        yaml.append("    dataSourceClassName: com.zaxxer.hikari.HikariDataSource\n");
        yaml.append("    jdbcUrl: ").append(db.getUrl()).append('\n');
        yaml.append("    username: ").append(db.getUsername()).append('\n');
        yaml.append("    password: ").append(db.getPassword()).append('\n');
        yaml.append("    driverClassName: ").append(db.getDriverClassName()).append('\n');
        yaml.append("    maximumPoolSize: ").append(db.getMaximumPoolSize()).append('\n');
        yaml.append("    minimumIdle: ").append(db.getMinimumIdle()).append('\n');
    }

    private void appendStrategy(StringBuilder yaml, String name) {
        yaml.append("        ").append(name).append(":\n");
        yaml.append("          standard:\n");
        yaml.append("            shardingColumn: ").append(SHARDING_COLUMN).append('\n');
        yaml.append("            shardingAlgorithmName: ").append(ALGORITHM_NAME).append('\n');
    }

}
