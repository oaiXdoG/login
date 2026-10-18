package com.oaixdog.loginserver.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class ApplicationProperties {

    @Value("${server.host}")
    private String host;

    @Value("${server.port}")
    private int port;

    @Value("${app.key}")
    private String appKey;

    @Value("${ticket-key}")
    private String loginTicketKey;

    @Value("${douyin.app-id}")
    private String douyinAppId;

    @Value("${douyin.app-secret}")
    private String douyinAppSecret;

    @Value("${redis.host}")
    private String redisHost;

    @Value("${redis.port}")
    private int redisPort;

    @Value("${redis.password}")
    private String redisPassword;

    @Value("${redis.database}")
    private int redisDatabase;

    @Value("${database.postgresql.url}")
    private String postgresqlUrl;

    @Value("${database.postgresql.username}")
    private String postgresqlUsername;

    @Value("${database.postgresql.password}")
    private String postgresqlPassword;

    @Value("${database.postgresql.hikari.maximum-pool-size}")
    private int postgresqlMaximumPoolSize;

    @Value("${database.postgresql.hikari.minimum-idle}")
    private int postgresqlMinimumIdle;

    @Value("${database.postgresql.hikari.connection-timeout}")
    private long postgresqlConnectionTimeout;

}
