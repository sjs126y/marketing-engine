package com.sjs.marketing.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 客户端，使用 Redisson <a href="https://github.com/redisson/redisson">Redisson</a>
 *
 * <p>统一使用 Redisson 自带的 {@link JsonJacksonCodec}：它写入了类型信息，
 * 可以直接把领域对象放进 Redis 再按类型读回，不需要自定义 codec。</p>
 *
 * @author marketing-engine contributors
 */
@Configuration
@EnableConfigurationProperties(RedisClientConfigProperties.class)
public class RedisClientConfig {

    @Bean("redissonClient")
    public RedissonClient redissonClient(ApplicationContext applicationContext, RedisClientConfigProperties properties) {
        Config config = new Config();
        config.setCodec(JsonJacksonCodec.INSTANCE);

        config.useSingleServer()
                .setAddress("redis://" + properties.getHost() + ":" + properties.getPort())
//                .setPassword(properties.getPassword())
                .setConnectionPoolSize(properties.getPoolSize())
                .setConnectionMinimumIdleSize(properties.getMinIdleSize())
                .setIdleConnectionTimeout(properties.getIdleTimeout())
                .setConnectTimeout(properties.getConnectTimeout())
                .setRetryAttempts(properties.getRetryAttempts())
                .setRetryInterval(properties.getRetryInterval())
                .setPingConnectionInterval(properties.getPingInterval())
                .setKeepAlive(properties.isKeepAlive())
                .setDatabase(1);

        return Redisson.create(config);
    }

}
