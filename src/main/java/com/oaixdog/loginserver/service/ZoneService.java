package com.oaixdog.loginserver.service;

import com.oaixdog.loginserver.protocol.LoginReport;
import com.oaixdog.loginserver.protocol.LoginReportAnswer;
import com.zfoo.event.anno.EventReceiver;
import com.zfoo.net.core.event.ServerSessionInactiveEvent;
import com.zfoo.net.session.Session;
import com.zfoo.protocol.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「区 → 游戏服」表，存在 Redis 里，多台 login 共用一份。
 * <p>
 * 游戏服定时上报（LoginReport），每次上报只会到其中一台 login，收到的那台写入 Redis：
 * key 为 server:{serverId}，值为上报内容的 JSON，30 秒过期，每次上报续期。key 存在即在线。
 * 同一区被另一个进程（instanceId 不同）占用且未过期时拒绝上报。判断和写入在 Lua 脚本里完成，保证原子。
 * 游戏服的连接断开时，收到它上报的那台 login 删除这个 key（仍是这个进程时才删），不用等过期。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZoneService {

    private static final Duration ONLINE_TIMEOUT = Duration.ofSeconds(30);
    private static final String ZONE_CONFLICT_MESSAGE = "区 ID 已被其他进程使用";

    /** KEYS[1] 区的 key；ARGV[1] instanceId，ARGV[2] 上报内容 JSON，ARGV[3] 过期毫秒。返回 0 拒绝，1 续期，2 新写入（新进程） */
    private static final RedisScript<Long> REPORT_SCRIPT = new DefaultRedisScript<>("""
            local old = redis.call('GET', KEYS[1])
            if old then
                if cjson.decode(old).instanceId ~= ARGV[1] then return 0 end
                redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
                return 1
            end
            redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
            return 2
            """, Long.class);

    /** KEYS[1] 区的 key；ARGV[1] instanceId。仍是这个进程时删除，返回删除的个数 */
    private static final RedisScript<Long> DELETE_SCRIPT = new DefaultRedisScript<>("""
            local old = redis.call('GET', KEYS[1])
            if old and cjson.decode(old).instanceId == ARGV[1] then return redis.call('DEL', KEYS[1]) end
            return 0
            """, Long.class);

    private final RedisService redisService;

    /** 本机收到过上报的连接 → 它占用的区，连接断开时据此删除 key */
    private final Map<Long, Owner> owners = new ConcurrentHashMap<>();

    public LoginReportAnswer report(Session session, LoginReport report) {
        String key = zoneKey(report.getServerId());
        long result = redisService.execute(REPORT_SCRIPT, key, report.getInstanceId(), JsonUtils.object2String(report),
                String.valueOf(ONLINE_TIMEOUT.toMillis()));
        if (result == 0) {
            log.warn("区 ID 已被其他进程使用，拒绝上报，serverId={}，instanceId={}，address={}",
                    report.getServerId(), report.getInstanceId(), session.getChannel().remoteAddress());
            return LoginReportAnswer.valueOf(false, ZONE_CONFLICT_MESSAGE);
        }
        if (result == 2) {
            log.info("收到游戏服上报，serverId={}，host={}，port={}，instanceId={}",
                    report.getServerId(), report.getHost(), report.getPort(), report.getInstanceId());
        }
        owners.put(session.getSid(), new Owner(report.getServerId(), report.getInstanceId()));
        return LoginReportAnswer.valueOf(true, "");
    }

    /**
     * 该区在线时返回上报内容，否则返回 null。
     */
    public LoginReport online(int serverId) {
        String value = redisService.get(zoneKey(serverId));
        return value == null ? null : JsonUtils.string2Object(value, LoginReport.class);
    }

    @EventReceiver
    public void onServerSessionInactiveEvent(ServerSessionInactiveEvent event) {
        Owner owner = owners.remove(event.getSession().getSid());
        if (owner == null) {
            return;
        }
        long deleted = redisService.execute(DELETE_SCRIPT, zoneKey(owner.serverId()), owner.instanceId());
        if (deleted > 0) {
            log.info("游戏服断开连接，区下线，serverId={}，instanceId={}", owner.serverId(), owner.instanceId());
        }
    }

    private static String zoneKey(int serverId) {
        return "server:" + serverId;
    }

    private record Owner(int serverId, String instanceId) {}
}
