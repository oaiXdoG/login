package com.oaixdog.loginserver.service;

import com.oaixdog.loginserver.config.ApplicationProperties;
import com.oaixdog.loginserver.entity.LoginUser;
import com.oaixdog.loginserver.exception.BusinessException;
import com.oaixdog.loginserver.mapper.LoginUserMapper;
import com.oaixdog.loginserver.pojo.LoginTicketData;
import com.oaixdog.loginserver.protocol.LoginReport;
import com.zfoo.protocol.util.JsonUtils;
import com.zfoo.protocol.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    private static final int LIVE_DATA_TASK_RETRY_TIMES = 3;
    private static final String DOUYIN_PLATFORM = "douyin";
    private static final String LOCAL_PLATFORM = "local";
    private static final int DEFAULT_ZONE_ID = 1;
    private static final Duration ROOM_ROUTE_TTL = Duration.ofHours(12);
    private static final List<String> LIVE_DATA_MESSAGE_TYPES = List.of("live_comment", "live_gift", "live_like", "live_fansclub");

    private final ApplicationProperties properties;
    private final DouyinOpenApiService douyinOpenApiService;
    private final LoginUserMapper loginUserMapper;
    private final LoginTicketService loginTicketService;
    private final VersionService versionService;
    private final ZoneService zoneService;
    private final RedisService redisService;

    public LoginResult login(String platform, String account, String token, String clientVersion, String resourceVersion, String loginIp) {
        VersionService.VersionCheckResult versionResult = versionService.checkVersion(clientVersion, resourceVersion);
        if (versionResult.needClientUpdate()) {
            throw new BusinessException("客户端版本过低，请更新至 " + versionResult.serverClientVersion());
        }
        if (versionResult.needResourceUpdate()) {
            throw new BusinessException("客户端资源版本过低，请更新至 " + versionResult.serverResourceVersion());
        }

        LoginReport logic = zoneService.online(DEFAULT_ZONE_ID);
        if (logic == null) {
            throw new BusinessException("服务器维护中");
        }

        LoginUser user;
        if (StringUtils.isNotBlank(account)) {
            user = loginUserMapper.upsertLocal(LOCAL_PLATFORM, properties.getDouyinAppId(), account.trim(), loginIp);
        } else {
            if (StringUtils.isBlank(platform)) {
                throw new BusinessException("登录平台不能为空");
            }
            if (!DOUYIN_PLATFORM.equalsIgnoreCase(platform.trim())) {
                throw new BusinessException("暂不支持登录平台 " + platform.trim());
            }
            if (StringUtils.isBlank(token)) {
                throw new BusinessException("抖音玩法启动 token 不能为空");
            }
            DouyinOpenApiService.LiveRoomInfo liveRoomInfo = douyinOpenApiService.getLiveRoomInfo(token);
            if (liveRoomInfo.roomId() <= 0) {
                throw new IllegalStateException("抖音直播信息中没有直播间 ID");
            }
            if (StringUtils.isBlank(liveRoomInfo.anchorOpenId())) {
                throw new IllegalStateException("抖音直播信息中没有主播身份");
            }
            startLiveDataTasks(liveRoomInfo.roomId());
            // 回调服按直播间找区：直播间 → 区号，每次登录覆盖
            String roomRoute = JsonUtils.object2String(Map.of("zoneId", DEFAULT_ZONE_ID));
            redisService.set("danmu:" + properties.getAppKey() + ":room:" + liveRoomInfo.roomId(), roomRoute, ROOM_ROUTE_TTL);
            String nickname = liveRoomInfo.nickName() == null ? "" : liveRoomInfo.nickName();
            String avatarUrl = liveRoomInfo.avatarUrl() == null ? "" : liveRoomInfo.avatarUrl();
            user = loginUserMapper.upsert(DOUYIN_PLATFORM, properties.getDouyinAppId(), liveRoomInfo.anchorOpenId(), nickname, avatarUrl, loginIp);
        }

        LoginTicketData ticketData = LoginTicketData.valueOf(user.getId(), user.getAnchorOpenId(), DEFAULT_ZONE_ID);
        String loginJson = JsonUtils.object2String(ticketData);
        String loginTicket = loginTicketService.encrypt(loginJson);

        return new LoginResult(loginTicket, logic.getHost(), logic.getPort());
    }

    private void startLiveDataTasks(long roomId) {
        for (String messageType : LIVE_DATA_MESSAGE_TYPES) {
            RuntimeException startException = null;
            for (int retry = 0; retry <= LIVE_DATA_TASK_RETRY_TIMES; retry++) {
                try {
                    douyinOpenApiService.startLiveDataTask(roomId, messageType);
                    startException = null;
                    break;
                } catch (RuntimeException exception) {
                    startException = exception;
                }
            }
            if (startException == null) {
                continue;
            }
            for (String taskMessageType : LIVE_DATA_MESSAGE_TYPES) {
                try {
                    douyinOpenApiService.stopLiveDataTask(roomId, taskMessageType);
                } catch (RuntimeException stopException) {
                    log.error("关闭抖音推送任务失败，roomId={}，messageType={}", roomId, taskMessageType, stopException);
                }
            }
            throw new IllegalStateException("开启抖音推送任务失败，messageType=" + messageType, startException);
        }
    }

    public record LoginResult(String loginTicket, String host, int port) {}
}
