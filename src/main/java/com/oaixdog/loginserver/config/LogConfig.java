package com.oaixdog.loginserver.config;

import com.oaixdog.logsdk.LogReporter;
import com.oaixdog.logsdk.LogSdkConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

/**
 * 初始化事件日志（log-sdk）：写到 log.dir，由本机 Vector 采集到 ClickHouse，管理后台日志中心按 App Key 查询。
 * 参数有误时抛异常，启动失败；之后上报不抛异常、不阻塞。
 */
@Configuration
@RequiredArgsConstructor
public class LogConfig {

    private final ApplicationProperties properties;

    @PostConstruct
    public void init() {
        LogReporter.init(LogSdkConfig.builder()
                .appKey(properties.getAppKey())
                .server(properties.getNodeId())
                .logDir(properties.getLogDir())
                .build());
    }
}
