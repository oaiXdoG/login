package com.oaixdog.loginserver.service;

import com.oaixdog.loginserver.protocol.LoginReport;
import com.oaixdog.loginserver.protocol.LoginReportAnswer;
import com.zfoo.net.session.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「区 → 游戏服」表，按游戏服上报维护，规则见 docs/service-discovery.md。
 * 记录不删除（断开连接也不删），只靠在线判断决定能不能用。
 */
@Slf4j
@Service
public class ZoneService {

    private static final Duration ONLINE_TIMEOUT = Duration.ofSeconds(30);
    private static final String ZONE_CONFLICT_MESSAGE = "区 ID 已被其他进程使用";

    private final Map<Integer, Zone> zones = new ConcurrentHashMap<>();

    public LoginReportAnswer report(Session session, LoginReport report) {
        boolean[] accepted = {true};
        // 同一区的判断和写入在 compute 中完成，保证原子
        zones.compute(report.getServerId(), (zoneId, old) -> {
            long now = System.currentTimeMillis();
            boolean sameInstance = old != null && Objects.equals(old.report().getInstanceId(), report.getInstanceId());
            if (old != null && !sameInstance && isOnline(old, now)) {
                accepted[0] = false;
                log.warn("区 ID 已被其他进程使用，拒绝上报，zoneId={}，oldInstanceId={}，newInstanceId={}，newAddress={}",
                        zoneId, old.report().getInstanceId(), report.getInstanceId(), session.getChannel().remoteAddress());
                return old;
            }
            if (!sameInstance) {
                log.info("收到游戏服上报，zoneId={}，host={}，port={}，instanceId={}", zoneId, report.getHost(), report.getPort(), report.getInstanceId());
            }
            return new Zone(report, session, now);
        });
        return accepted[0] ? LoginReportAnswer.valueOf(true, "") : LoginReportAnswer.valueOf(false, ZONE_CONFLICT_MESSAGE);
    }

    /**
     * 该区在线时返回上报内容，否则返回 null。
     */
    public LoginReport online(int zoneId) {
        Zone zone = zones.get(zoneId);
        if (zone == null || !isOnline(zone, System.currentTimeMillis())) {
            return null;
        }
        return zone.report();
    }

    /**
     * 连接可用且 30 秒内有上报。
     */
    private boolean isOnline(Zone zone, long now) {
        return zone.session().getChannel().isActive() && now - zone.reportTime() <= ONLINE_TIMEOUT.toMillis();
    }

    private record Zone(LoginReport report, Session session, long reportTime) {}
}
