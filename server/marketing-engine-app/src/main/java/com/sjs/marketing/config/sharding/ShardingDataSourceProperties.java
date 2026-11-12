package com.sjs.marketing.config.sharding;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 分库分表的数据源配置：db0 为配置库，db1/db2 为分片库。
 *
 * @author marketing-engine contributors
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.sharding")
public class ShardingDataSourceProperties {

    private Db db0 = new Db();
    private Db db1 = new Db();
    private Db db2 = new Db();

    @Data
    public static class Db {
        private String url;
        private String username;
        private String password;
        private String driverClassName = "com.mysql.cj.jdbc.Driver";
        private int maximumPoolSize = 20;
        private int minimumIdle = 5;
    }

}
