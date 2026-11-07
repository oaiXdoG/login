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

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    private static final int LIVE_DATA_TASK_RETRY_TIMES = 3;
    private static final String DOUYIN_PLATFORM = "douyin";
    private static final String LOCAL_PLATFORM = "local";
    private static final List<String> LIVE_DATA_MESSAGE_TYPES = List.of("live_comment", "live_gift", "live_like", "live_fansclub");

    private final ApplicationProperties properties;
    private final DouyinOpenApiService douyinOpenApiService;
    private final LoginUserMapper loginUserMapper;
    private final LoginTicketService loginTicketService;
    private final VersionService versionService;
    private final GameOnlineService gameOnlineService;
    private final EventReportService eventReportService;

    public LoginResult login(String platform, String account, String token, String clientVersion, String resourceVersion, String loginIp) {
        VersionService.VersionCheckResult versionResult = versionService.checkVersion(clientVersion, resourceVersion);
        if (versionResult.needClientUpdate()) {
            throw new BusinessException("客户端版本过低，请更新至 " + versionResult.serverClientVersion());
        }
        if (versionResult.needResourceUpdate()) {
            throw new BusinessException("客户端资源版本过低，请更新至 " + versionResult.serverResourceVersion());
        }

        LoginReport logic = gameOnlineService.online();
        if (logic == null) {
            throw new BusinessException("服务器维护中");
        }

        LoginUser user;
        String loginPlatform;
        // 直播间 ID 统一按字符串上报，与回调服的 x-roomid 一致；本地测试登录为空
        String roomId = "";
        if (StringUtils.isNotBlank(account)) {
            loginPlatform = LOCAL_PLATFORM;
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
            loginPlatform = DOUYIN_PLATFORM;
            roomId = String.valueOf(liveRoomInfo.roomId());
            startLiveDataTasks(liveRoomInfo.roomId());
            String nickname = liveRoomInfo.nickName() == null ? "" : liveRoomInfo.nickName();
            String avatarUrl = liveRoomInfo.avatarUrl() == null ? "" : liveRoomInfo.avatarUrl();
            user = loginUserMapper.upsert(DOUYIN_PLATFORM, properties.getDouyinAppId(), liveRoomInfo.anchorOpenId(), nickname, avatarUrl, loginIp);
        }

        // 票据带过期时间，游戏服解开后超过 expireAt 即拒绝，客户端重新登录
        long expireAt = System.currentTimeMillis() + properties.getTicketExpireSeconds() * 1000L;
        LoginTicketData ticketData = LoginTicketData.valueOf(user.getId(), user.getAnchorOpenId(), expireAt);
        String loginJson = JsonUtils.object2String(ticketData);
        String loginTicket = loginTicketService.encrypt(loginJson);

        // userId 是登录服账号 ID（login_user.id），游戏服的事件里以 userId 关联
        eventReportService.report("login",
                "userId", user.getId(),
                "platform", loginPlatform,
                "roomId", roomId,
                "ip", loginIp,
                "clientVersion", clientVersion,
                "resourceVersion", resourceVersion);

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
